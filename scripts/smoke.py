#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Run the real sourcing workflow only in an explicitly authorized local test deployment."""
import argparse, json, urllib.request, urllib.error, urllib.parse, http.cookiejar, secrets, time, datetime, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8097');p.add_argument('--allow-test-data',action='store_true');p.add_argument('--browser-credentials');p.add_argument('--verify-existing',type=int);args=p.parse_args()
assert args.allow_test_data and urllib.parse.urlsplit(args.base).hostname in {'127.0.0.1','localhost','::1'},'Only an explicitly authorized local test environment is allowed'
root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
checks=[]
def check(name,condition):
    assert condition,name
    checks.append(name)
    print('PASS '+name,flush=True)
class Client:
    def __init__(self):self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
    def request(self,method,path,body=None,expected=200,csrf=True):
        if method!='GET' and csrf and self.csrf is None:self.csrf=self.request('GET','/api/auth/csrf')
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf:headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with self.http.open(req,timeout=30) as r:status=r.status;content=r.read()
        except urllib.error.HTTPError as e:status=e.code;content=e.read()
        value=json.loads(content) if content else None
        assert status==expected,f'{method} {path}: expected {expected}, got {status}, code={value.get("code") if isinstance(value,dict) else "unknown"}'
        return value
    def login(self,user,password):return self.request('POST','/api/auth/login',{'username':user,'password':password})
admin=Client();anonymous=Client()
check('health',anonymous.request('GET','/actuator/health')['status']=='UP')
check('anonymous rejected',anonymous.request('GET','/api/rfqs',expected=401)['code']=='UNAUTHENTICATED')
check('admin login',admin.login('admin',env['ADMIN_PASSWORD'])['username']=='admin')
if args.verify_existing:
    r=admin.request('GET',f'/api/rfqs/{args.verify_existing}/advice.json')
    check('restart preserved frozen advice',r['kind']=='PROCUREMENT_ADVICE')
    print(json.dumps({'checks':len(checks),'rfqId':args.verify_existing,'result':'PASS'}));raise SystemExit
check('CSRF enforced',admin.request('POST','/api/admin/departments',{'name':'should not save'},expected=403,csrf=False)['code']=='FORBIDDEN')
suffix=secrets.token_hex(3);password='Aa9'+secrets.token_urlsafe(20)
department=admin.request('POST','/api/admin/departments',{'name':'验收寻源部-'+suffix})['id']
roles={r['name']:r['id'] for r in admin.request('GET','/api/admin/roles')}
def account(username,role,supplier=None):
    admin.request('POST','/api/admin/users',{'username':username,'displayName':'验收-'+username,'password':password,'roleId':roles[role],'departmentId':department,'supplierId':supplier,'enabled':True})
    client=Client();client.login(username,password);return client
buyer_name='qa-buyer-'+suffix;manager_name='qa-review-'+suffix;buyer=account(buyer_name,'采购专员');manager=account(manager_name,'审批负责人')
supplier_ids=[]
for code,name in [('QA-A-'+suffix,'验收供应商甲'),('QA-B-'+suffix,'验收供应商乙')]:
    s=buyer.request('POST','/api/suppliers',{'code':code,'name':name,'category':'设备配件','contact':'qa@example.invalid','qualification':'虚构验收资质：仅用于系统测试','validUntil':(datetime.date.today()+datetime.timedelta(days=365)).isoformat(),'enabled':True})
    manager.request('POST',f'/api/suppliers/{s["id"]}/approve',{'version':s['version'],'note':'虚构资料准入验收'})
    supplier_ids.append(s['id'])
check('supplier admission persisted',all(s['status']=='APPROVED' for s in buyer.request('GET','/api/suppliers')))
s1_name='qa-supplier-'+suffix;s1=account(s1_name,'供应商',supplier_ids[0]);s2=account('qa-supplier-b-'+suffix,'供应商',supplier_ids[1])
check('supplier own directory',len(s1.request('GET','/api/suppliers'))==1)
check('supplier admin rejected',s1.request('GET','/api/admin/users',expected=403)['code']=='FORBIDDEN')
check('buyer admin rejected',buyer.request('GET','/api/admin/users',expected=403)['code']=='FORBIDDEN')
now=datetime.datetime.now(datetime.timezone.utc);deadline=now+datetime.timedelta(seconds=12)
draft={'title':'验收：设备配件年度询价','description':'虚构学习数据。比较供货交期、单价与付款条款。','category':'设备配件','currency':'CNY','departmentId':department,'deadline':deadline.isoformat(),'supplierIds':supplier_ids,'lines':[{'itemCode':'QA-PART-01','name':'验收设备配件','specification':'测试规格，非真实采购需求','unit':'件','quantity':3}]}
d=buyer.request('POST','/api/rfqs',draft);rid=d['rfq']['id'];check('draft persisted',d['rfq']['status']=='DRAFT')
def command():return {'version':d['rfq']['version'],'requestKey':secrets.token_hex(16),'note':'虚构验收审批意见'}
def action(client,name,body=None):return client.request('POST',f'/api/rfqs/{rid}/{name}',body or command())
d=action(buyer,'submit');check('submitted for review',d['rfq']['status']=='PENDING_APPROVAL')
check('supplier draft hidden',s1.request('GET',f'/api/rfqs/{rid}',expected=403)['code']=='OUT_OF_SCOPE')
d=action(manager,'approve');check('approved and open',d['rfq']['status']=='OPEN')
check('cannot close early',buyer.request('POST',f'/api/rfqs/{rid}/close',command(),expected=409)['code']=='BEFORE_DEADLINE')
def quote(price):return {'requestKey':secrets.token_hex(16),'leadDays':7,'freight':5,'terms':'验收：交货后核对票据，30日付款','validUntil':(now+datetime.timedelta(days=30)).isoformat(),'lines':[{'rfqLineId':d['lines'][0]['id'],'unitPrice':price,'taxRate':13}]}
first=quote('10');a=action(s1,'quote',first);a=action(s1,'quote',first);check('quote retry idempotent',len(a['quotes'])==1)
a=action(s1,'quote',quote('3.335'));check('revisions retained',len(a['quotes'])==2 and a['quotes'][1]['status']=='SUPERSEDED')
b=action(s2,'quote',quote('4'))
check('supplier sees only own quotes',all(q['supplierId']==supplier_ids[0] for q in s1.request('GET',f'/api/rfqs/{rid}')['quotes']))
d=buyer.request('GET',f'/api/rfqs/{rid}');check('sealed API hides prices',all('quote' not in q for q in d['quotes']))
check('published draft immutable',buyer.request('PUT',f'/api/rfqs/{rid}',dict(draft,version=d['rfq']['version']),expected=409)['code']=='INVALID_STATE')
wait=max(0,(deadline-datetime.datetime.now(datetime.timezone.utc)).total_seconds()+0.1);time.sleep(wait)
check('quote deadline enforced',s1.request('POST',f'/api/rfqs/{rid}/quote',quote('20'),expected=409)['code']=='DEADLINE_PASSED')
d=action(buyer,'close');check('closed reveals prices',not d['sealed'] and all('quote' in q for q in d['quotes']))
q=next(q for q in d['quotes'] if q['supplierId']==supplier_ids[0] and q['status']=='SUBMITTED');check('server tax and freight',float(q['quote']['grossTotal'])==16.31)
c=dict(command(),quoteId=q['id'],note='验收：价格较低且交期符合要求');d=action(buyer,'select',c);check('award requires independent approval',d['rfq']['status']=='PENDING_AWARD')
d=action(manager,'award');advice=buyer.request('GET',f'/api/rfqs/{rid}/advice.json');check('award frozen advice',advice['grossTotal']==16.31 and advice['schemaVersion']=='1.0')
check('supplier result without competitor detail',s1.request('GET',f'/api/rfqs/{rid}')['rfq']['won'] and len(s1.request('GET',f'/api/rfqs/{rid}')['quotes'])==2)
check('supplier cannot export',s1.request('GET',f'/api/rfqs/{rid}/advice.json',expected=403)['code']=='FORBIDDEN')
check('awarded cannot cancel',buyer.request('POST',f'/api/rfqs/{rid}/cancel',command(),expected=409)['code']=='INVALID_STATE')
check('search pagination',buyer.request('GET','/api/rfqs?search='+urllib.parse.quote('年度')+'&size=1')['total']==1)
check('scoped statistics',buyer.request('GET','/api/dashboard')['awardedAmounts']['CNY']==16.31)
check('audit events persisted',any(a['action']=='RFQ_AWARD' and a['objectId']==str(rid) for a in manager.request('GET','/api/audit')))
# Create an open example for real portal screenshots; all records remain clearly marked as tests.
draft['title']='验收：生产耗材季度询价';draft['deadline']=(now+datetime.timedelta(days=7)).isoformat();draft['lines'][0]['itemCode']='QA-PART-02'
open_detail=buyer.request('POST','/api/rfqs',draft);open_id=open_detail['rfq']['id']
for client,name in [(buyer,'submit'),(manager,'approve')]:open_detail=client.request('POST',f'/api/rfqs/{open_id}/{name}',{'version':open_detail['rfq']['version'],'requestKey':secrets.token_hex(16),'note':'虚构验收发布意见'})
if args.browser_credentials:
    target=Path(args.browser_credentials);assert root not in target.parents,'Do not write browser credentials inside public project'
    fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out:json.dump({'buyer':buyer_name,'manager':manager_name,'supplier':s1_name,'password':password,'awardedId':rid,'openId':open_id},out)
print(json.dumps({'checks':len(checks),'rfqId':rid,'openRfqId':open_id,'result':'PASS'}))

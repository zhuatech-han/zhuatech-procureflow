#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Validate public tracked artifacts, attribution, original brand hashes and sensitive patterns."""
from pathlib import Path
import hashlib, re, json, subprocess
root=Path(__file__).resolve().parents[1]
readme=(root/'README.md').read_text();license=(root/'LICENSE').read_text()
images=re.findall(r'!\[[^\]]*\]\(([^)]+)\)',readme)+re.findall(r'<img[^>]+src="([^"]+)"',readme)
for image in images:
    target=root/image
    assert target.is_file() and target.stat().st_size>1000,f'Missing image {image}'
expected={'docs/images/wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','docs/images/wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
for name,digest in expected.items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,f'Original QR changed {name}'
for value in ['上海如静知华信息科技有限公司','https://www.zhuatech.cn/','zhuatech2','未经书面授权不得商用']:assert value in readme and value in license
assert 'Non-Commercial Source License' in license
patterns=[r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----',r'gh[pousr]_[A-Za-z0-9]{30,}',r'github_pat_[A-Za-z0-9_]{40,}',r'AKIA[A-Z0-9]{16}',r'(?i)(?:password|secret|token)\s*[=:]\s*["\'][A-Za-z0-9+/=_-]{18,}["\']']
files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=root).decode().split('\0')
count=0
for name in set(files):
    if not name:continue
    file=root/name
    assert file.name not in {'.env'} and not file.name.endswith('.log'),f'Private config or log in publication {name}'
    if file.suffix in {'.png','.jpg'}:continue
    text=file.read_text();count+=1
    if file.suffix in {'.java','.js','.vue','.css','.sql','.py'} and not name.startswith(('docs/licenses/','frontend/public/third-party/')):assert '上海如静知华信息科技有限公司' in text,f'Missing attribution {name}'
    for pattern in patterns:assert not re.search(pattern,text),f'Sensitive pattern in {name}'
assert len(list((root/'docs/screenshots').glob('*.jpg'))) >=6
assert '.env' not in files
print(json.dumps({'filesScanned':count,'readmeImages':len(images),'sixRealScreenshots':'PASS','originalQrHashes':'PASS','brandLicense':'PASS','sensitivePatternScan':'PASS'}))

#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Generate independent local credentials without displaying secrets or overwriting files."""
from pathlib import Path
import secrets
import os
p=Path(__file__).resolve().parents[1]/'.env'
values={key:'Aa9'+secrets.token_urlsafe(24) for key in ['DATABASE_PASSWORD','MYSQL_ROOT_PASSWORD','ADMIN_PASSWORD']}
fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:
    out.write(''.join(f'{key}={value}\n' for key,value in values.items())+'WEB_PORT=8097\nBIND_ADDRESS=127.0.0.1\nCOOKIE_SECURE=false\n')
print('Local configuration created; credentials are in ignored .env.')

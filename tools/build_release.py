"""Package source deployment artifacts using explicit roots, never runtime secrets."""
from pathlib import Path
import hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
fixed=['GUIDE.md','backend/payment.env.example','backend/order-timeout.env.example','README.md','.gitignore','backend/pom.xml','backend/Dockerfile','backend/.dockerignore','admin/package.json','admin/package-lock.json','admin/tsconfig.json','admin/vite.config.ts','admin/index.html','admin/Dockerfile','admin/.dockerignore']
roots=['backend/src','admin/src','admin/public','miniprogram','docs','design','doc','tests','tools']
files={ROOT/name for name in fixed if (ROOT/name).is_file()}
for directory in roots:
 if (ROOT/directory).exists():files.update(p for p in (ROOT/directory).rglob('*') if p.is_file())
for name in ['compose.yml','nginx.conf','.env.example','.dockerignore','minio.Dockerfile','mc.Dockerfile','VERIFICATION.md']:
 if (ROOT/'deploy'/name).is_file():files.add(ROOT/'deploy'/name)
for directory in ['deploy/scripts','deploy/config']:
 files.update(p for p in (ROOT/directory).rglob('*') if p.is_file())
def allowed(p):
 if p.is_symlink():return False
 rel=p.relative_to(ROOT)
 if any(part in {'node_modules','target','dist','__pycache__','.git','secrets','certs','backups'} for part in rel.parts):return False
 if p.name in {'.env','notifications.yml','project.private.config.json'}:return False
 if p.suffix.lower() in {'.pem','.key','.env','.pyc'}:return False
 if any(part.startswith('.') for part in rel.parts) and p.name not in {'.gitignore','.dockerignore','.env.example'}:return False
 return True
files=sorted((p for p in files if allowed(p)),key=lambda p:p.relative_to(ROOT).as_posix())
output=ROOT/'dist';output.mkdir(exist_ok=True);target=output/'fishmall-delivery-20261029.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for p in files:
  info=zipfile.ZipInfo('fishmall/'+p.relative_to(ROOT).as_posix(),date_time=(2026,10,29,0,0,0));info.external_attr=(0o644 & 0xffff)<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
digest=hashlib.sha256(target.read_bytes()).hexdigest()
(target.with_suffix('.zip.sha256')).write_text(digest+'  '+target.name+'\n',encoding='ascii')
print('Packaged',len(files),'files:',target);print('SHA256:',digest)

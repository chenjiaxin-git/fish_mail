"""Only for a disposable fishmall-drill-* Compose project. Never targets a business database."""
import base64, hashlib, hmac, json, ssl, subprocess, sys, time, urllib.request, urllib.error, uuid
from pathlib import Path
mode, env_path, expected_path = sys.argv[1:4]
env_path=Path(env_path).resolve(); values=dict(line.split('=',1) for line in env_path.read_text().splitlines() if line and not line.startswith('#'))
assert values['COMPOSE_PROJECT_NAME'].startswith('fishmall-drill-'), 'Only disposable drill projects are allowed'
assert values.get('WECHAT_PAY_ENABLED')=='false' and values.get('NOTIFICATION_ENABLED')=='false'
port=int(values['HTTPS_PORT']);assert 1024<port<65536
origin='https://127.0.0.1:'+str(port)
ctx=ssl.create_default_context(cafile=str(Path(values['CERT_DIR'])/'fullchain.pem'))
def raw(path,method='GET',body=None,token=None,content_type='application/json'):
 headers={}
 if token:headers['Authorization']='Bearer '+token
 if body is not None:headers['Content-Type']=content_type
 request=urllib.request.Request(origin+path,data=body,headers=headers,method=method)
 with urllib.request.urlopen(request,context=ctx,timeout=30) as response:return response.read(),response.headers

def api(path,method='GET',data=None,token=None):
 value=json.loads(raw('/api/v1'+path,method,None if data is None else json.dumps(data,ensure_ascii=False).encode(),token)[0]);assert value['code']=='0',value;return value['data']
assert b'id="app"' in raw('/orders/123')[0], 'Nginx history fallback failed'
for path in ['/actuator/health','/api/v1/users/subscription-config']:
 try:raw(path);raise AssertionError('Unexpected anonymous access')
 except urllib.error.HTTPError as error:assert error.code==(404 if path.startswith('/actuator') else 401)
admin=api('/admin/login','POST',{'username':'admin','password':values['ADMIN_INITIAL_PASSWORD']})['accessToken']
if mode=='seed':
 png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jL1kAAAAASUVORK5CYII=')
 boundary='drill'+uuid.uuid4().hex
 body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="drill.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+('\r\n--'+boundary+'--\r\n').encode()
 uploaded=json.loads(raw('/api/v1/admin/files','POST',body,admin,'multipart/form-data; boundary='+boundary)[0]);assert uploaded['code']=='0',uploaded
 image=uploaded['data']['url'];assert raw(image)[0]==png
 category=api('/admin/categories','POST',{'name':'隔离验收分类','sort':0,'status':'ACTIVE'},admin)
 spec={'id':None,'name':'按重量','quantityKind':'WEIGHT','unit':'kg','priceCent':3000,'minQuantity':0.5,'stepQuantity':0.5,'physicalStockQty':20,'warningQty':1,'status':'ACTIVE'}
 product=api('/admin/products','POST',{'categoryId':category['id'],'name':'隔离验收鲜鱼','subtitle':'演练数据','description':'仅用于一次性恢复验证','mainImageUrl':image,'status':'ACTIVE','specs':[spec]},admin)
 command=['docker','compose','--env-file',str(env_path),'-f',str(Path('deploy/compose.yml').resolve()),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" -N fishmall']
 result=subprocess.run(command,input=b"insert into wx_user(openid) values ('drill-only-user'); select id from wx_user where openid='drill-only-user';",capture_output=True,check=True)
 user_id=int(result.stdout.decode().strip())
 enc=lambda x:base64.urlsafe_b64encode(json.dumps(x,separators=(',',':')).encode()).rstrip(b'=')
 now=int(time.time());signing=enc({'alg':'HS256','typ':'JWT'})+b'.'+enc({'wxUserId':user_id,'role':'USER','iat':now,'exp':now+3600,'iss':'fishmall-user','aud':'fishmall-miniprogram'})
 user_token=(signing+b'.'+base64.urlsafe_b64encode(hmac.new(values['JWT_USER_SECRET'].encode(),signing,hashlib.sha256).digest()).rstrip(b'=')).decode()
 config=api('/users/subscription-config',token=user_token);assert config['enabled'] is False and config['templates']=={}
 address=api('/addresses','POST',{'contactName':'验收用户','phone':'13800138000','detailAddress':'隔离测试地址','isDefault':True},user_token)
 order=api('/orders','POST',{'addressId':address['id'],'items':[{'productSpecId':product['specs'][0]['id'],'quantity':1.5}],'cartItemIds':[],'idempotencyKey':str(uuid.uuid4())},user_token)
 detail=api('/admin/orders/'+str(order['id']),token=admin)
 quoted=api('/admin/orders/'+str(order['id'])+'/quote','POST',{'quoteVersion':0,'items':[{'orderItemId':detail['items'][0]['id'],'actualQuantity':'1.200','finalAmountCent':3600}],'finalAmountCent':3600,'reason':'隔离备份恢复验收'},admin)
 assert quoted['status']=='QUOTED'
 Path(expected_path).write_text(json.dumps({'orderId':order['id'],'imageUrl':image,'sha256':hashlib.sha256(png).hexdigest(),'productId':product['id']},ensure_ascii=False),encoding='utf-8')
 print('PASS: HTTPS trust, history fallback, auth, private MinIO image upload/read, product, order creation and quote')
else:
 expected=json.loads(Path(expected_path).read_text(encoding='utf-8'))
 detail=api('/admin/orders/'+str(expected['orderId']),token=admin)
 assert detail['status']=='QUOTED' and detail['quoteVersion']==1 and detail['finalAmountCent']==3600
 assert str(detail['items'][0]['actualQuantity']) in ('1.2','1.200')
 assert hashlib.sha256(raw(expected['imageUrl'])[0]).hexdigest()==expected['sha256']
 legacy='/uploads/products/11111111-1111-1111-1111-111111111111.png'
 assert hashlib.sha256(raw(legacy)[0]).hexdigest()==expected['sha256']
 print('PASS: historical order, quote version, amount, quantity and both current/legacy image hashes after restore')

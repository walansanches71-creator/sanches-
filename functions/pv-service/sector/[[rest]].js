const upstream='https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-team';
const reply=(data,status=200)=>Response.json(data,{status,headers:{'cache-control':'no-store'}});
export async function onRequest({request,params}) {
 const path='/'+(Array.isArray(params.rest)?params.rest.join('/'):params.rest||'');
 const method=request.method;
 const read=method==='GET'&&(/^\/(me|demands|contacts|chat|chat\/inbox)$/.test(path)||/^\/demands\/[a-zA-Z0-9_-]+$/.test(path));
 const write=method==='POST'&&(/^\/(login|logout|chat)$/.test(path)||/^\/demands\/[a-zA-Z0-9_-]+\/route$/.test(path));
 if(!read&&!write)return reply({error:'Operação indisponível na área do setor.'},403);
 const headers={'content-type':'application/json','x-pv-session':request.headers.get('x-pv-session')||''};
 const call=(p,options={})=>fetch(upstream+p,{...options,headers,signal:AbortSignal.timeout(25000)});
 try {
  let body;
  if(method==='POST'&&path!=='/logout'){
   const raw=await request.text();if(raw.length>12000)return reply({error:'Conteúdo muito grande.'},413);
   try{body=JSON.parse(raw)}catch{return reply({error:'Dados inválidos.'},400)}
   if(path==='/login')body={name:body.name,pin:body.pin,kind:'sector'};
   else if(path==='/chat')body={id:body.id,room:body.room,text:body.text};
   else body={target:body.target,status:body.status,complete:body.complete,expected_updated_at:body.expected_updated_at};
  }
  if(path!=='/login'){
   if(!headers['x-pv-session'])return reply({error:'Entre com seu nome e código.'},401);
   const auth=await call('/me');const me=await auth.json();
   if(!auth.ok)return reply(me,auth.status);
   if(me.member?.kind!=='sector')return reply({error:'Use um acesso de setor.'},403);
   if(path==='/me')return reply(me);
  }
  const response=await call(path+new URL(request.url).search,{method,...(body?{body:JSON.stringify(body)}:{})});
  return reply(await response.json(),response.status);
 }catch{return reply({error:'Não foi possível conectar. Tente novamente.'},503)}
}

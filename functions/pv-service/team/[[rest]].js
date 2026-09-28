const BASE = 'https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-team/';
export async function onRequest({request,params}) {
 const path=Array.isArray(params.rest)?params.rest.join('/'):String(params.rest||'');
 const target=new URL(BASE+path);target.search=new URL(request.url).search;
 const headers=new Headers({'content-type':'application/json'});
 const session=request.headers.get('x-pv-session');if(session)headers.set('x-pv-session',session);
 const responseHeaders={'content-type':'application/json; charset=utf-8','cache-control':'no-store'};
 try {
  const upstream=await fetch(target,{method:request.method,headers,redirect:'manual',signal:AbortSignal.timeout(25000),
   ...(['GET','HEAD'].includes(request.method)?{}:{body:await request.arrayBuffer()})});
  return new Response(upstream.body,{status:upstream.status,headers:responseHeaders});
 }catch{return new Response(JSON.stringify({error:'Servidor indisponível. Tente novamente em instantes.'}),{status:503,headers:responseHeaders})}
}

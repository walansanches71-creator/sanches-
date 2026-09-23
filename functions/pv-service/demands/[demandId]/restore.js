const UPSTREAM = "https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-app/api/demands/";
async function proxy({request,params,restore=false}) {
  const id = encodeURIComponent(params.demandId || "");
  const target = new URL(UPSTREAM + id + (restore ? "/restore" : ""));
  target.search = new URL(request.url).search;
  const headers = new Headers();
  for (const name of ["authorization","apikey","content-type","x-pv-client"]) {
    const value = request.headers.get(name); if (value) headers.set(name,value);
  }
  const init = {method:request.method,headers,redirect:"manual"};
  if (request.method !== "GET" && request.method !== "HEAD") init.body = await request.arrayBuffer();
  const upstream = await fetch(target,init);
  const out = new Headers();
  for (const name of ["content-type","cache-control","retry-after"]) {const value=upstream.headers.get(name);if(value)out.set(name,value)}
  out.set("cache-control","no-store");
  return new Response(upstream.body,{status:upstream.status,headers:out});
}
export async function onRequest(context) { return proxy({...context,restore:true}); }

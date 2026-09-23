export async function onRequest({ request, params }) {
  const id = String(params.userId || "");
  if (!/^[0-9a-f-]{36}$/i.test(id)) return new Response(JSON.stringify({ok:false,error:"Conta inválida."}), {status:400,headers:{"content-type":"application/json"}});
  const target = new URL("https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-app/api/users/" + encodeURIComponent(id) + "/activation-code");
  const headers = new Headers();
  for (const name of ["authorization", "apikey", "content-type", "x-pv-client"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    const upstream = await fetch(target, {method:request.method,headers,body:request.method==="GET"||request.method==="HEAD"?undefined:await request.arrayBuffer(),redirect:"manual"});
    const responseHeaders = new Headers();
    for (const name of ["content-type", "cache-control", "retry-after"]) {
      const value = upstream.headers.get(name);
      if (value) responseHeaders.set(name, value);
    }
    responseHeaders.set("cache-control", "no-store");
    return new Response(upstream.body, {status:upstream.status,headers:responseHeaders});
  } catch {
    return new Response(JSON.stringify({ok:false,error:"Não foi possível conectar à API Produção VIP."}), {status:502,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}});
  }
}

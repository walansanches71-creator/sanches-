const UPSTREAM = "https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-app/api/demands";
export async function onRequest({ request }) {
  const target = new URL(UPSTREAM);
  target.search = new URL(request.url).search;
  const headers = new Headers();
  for (const name of ["authorization", "apikey", "content-type", "x-pv-client"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const init = { method: request.method, headers, redirect: "manual" };
  if (request.method !== "GET" && request.method !== "HEAD") init.body = await request.arrayBuffer();
  try {
    const upstream = await fetch(target, init);
    const responseHeaders = new Headers();
    for (const name of ["content-type", "cache-control", "retry-after"]) {
      const value = upstream.headers.get(name);
      if (value) responseHeaders.set(name, value);
    }
    responseHeaders.set("cache-control", "no-store");
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return new Response(JSON.stringify({ ok: false, error: "Não foi possível conectar à API Produção VIP." }), {
      status: 502, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
    });
  }
}

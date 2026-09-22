export async function onRequest({ request, params }) {
  const path = Array.isArray(params.rest) ? params.rest.join("/") : String(params.rest || "");
  if (!path || path.includes("..")) return new Response("Rota inválida.", { status: 400 });
  const incoming = new URL(request.url);
  const target = new URL("https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-app/" + path);
  target.search = incoming.search;
  const headers = new Headers();
  for (const name of ["authorization", "apikey", "content-type", "x-pv-client"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const init = { method: request.method, headers, redirect: "manual" };
  if (request.method !== "GET" && request.method !== "HEAD") init.body = await request.arrayBuffer();
  const upstream = await fetch(target, init);
  const responseHeaders = new Headers();
  for (const name of ["content-type", "cache-control", "retry-after"]) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  responseHeaders.set("cache-control", "no-store");
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

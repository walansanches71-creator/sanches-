const UPSTREAM = "https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-public-site/api";
export async function onRequest({ request, params }) {
  const rest = Array.isArray(params.rest) ? params.rest.join("/") : String(params.rest || "");
  const url = new URL(UPSTREAM + "/" + rest);
  url.search = new URL(request.url).search;
  const headers = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
  try {
    const upstream = await fetch(url, {
      method: request.method, headers: { "content-type": "application/json" },
      ...(request.method === "GET" || request.method === "HEAD" ? {} : { body: await request.arrayBuffer() }),
      redirect: "manual", signal: AbortSignal.timeout(16000)
    });
    return new Response(upstream.body, { status: upstream.status, headers });
  } catch {
    return new Response(JSON.stringify({ error: "O servidor de produção está temporariamente indisponível. Tente atualizar em instantes." }), { status: 503, headers });
  }
}

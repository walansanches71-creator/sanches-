const UPSTREAM = "https://ugqxcarzyjmybyrpdrqx.supabase.co/functions/v1/producao-vip-app/api/extension-login";

export async function onRequest({ request }) {
  const responseHeaders = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "Método não permitido." }), { status: 405, headers: { ...responseHeaders, allow: "POST" } });
  }
  const headers = new Headers({ "content-type": "application/json", "x-pv-client": "site" });
  const key = request.headers.get("apikey");
  if (key) headers.set("apikey", key);
  try {
    const upstream = await fetch(UPSTREAM, {
      method: "POST",
      headers,
      body: await request.arrayBuffer(),
      redirect: "manual",
      signal: AbortSignal.timeout(15000)
    });
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return new Response(JSON.stringify({ error: "Não foi possível conectar ao servidor de acesso. Tente novamente." }), { status: 502, headers: responseHeaders });
  }
}

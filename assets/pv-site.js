window.PVSite = {
  async api(path, options = {}) {
    const route = String(path).replace(/^\/api(?=\/)/, "");
    let response;
    try {
      response = await fetch("/pv-service/site" + route, {
        ...options, headers: { "content-type": "application/json", ...(options.headers || {}) },
        cache: "no-store", signal: AbortSignal.timeout(20000)
      });
    } catch {
      throw Error("Não foi possível conectar ao servidor de produção. Tente atualizar em instantes.");
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw Error(data.error || "O servidor de produção está temporariamente indisponível.");
    return data;
  }
};

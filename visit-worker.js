const CACHE='np-visit-v1',FILES=['/visita','/assets/visit-public.js','/assets/visit-public.css'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(FILES)));self.skipWaiting()});self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));
self.addEventListener('fetch',e=>{const u=new URL(e.request.url);if(e.request.method!=='GET'||u.origin!==location.origin||!FILES.includes(u.pathname))return;e.respondWith(fetch(e.request).catch(()=>caches.match(u.pathname)))});

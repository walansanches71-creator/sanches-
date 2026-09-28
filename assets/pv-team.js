/* Shared by the website and both packaged extensions. No credentials in URLs. */
(() => {
 const extension=location.protocol==='chrome-extension:';
 const base=extension?'https://producao-vip.pages.dev/pv-service/team':'/pv-service/team';
 let credentials=null;
 const ready=(async()=>{try{credentials=extension?(await chrome.storage.local.get('pvTeam')).pvTeam:JSON.parse(sessionStorage.getItem('pvTeam')||'null')}catch{credentials=null}})();
 async function remember(value){credentials=value;if(extension)await chrome.storage.local.set({pvTeam:value});else if(value)sessionStorage.setItem('pvTeam',JSON.stringify(value));else sessionStorage.removeItem('pvTeam')}
 async function api(path,options={}){
  await ready;let r;
  try{r=await fetch(base+path,{...options,headers:{'content-type':'application/json',...(credentials?.token?{'x-pv-session':credentials.token}:{}),...(options.headers||{})},cache:'no-store',signal:AbortSignal.timeout(30000)})}
  catch{throw Error('Sem conexão com o site. As alterações locais estão preservadas.')}
  const data=await r.json().catch(()=>({}));
  if(!r.ok){const e=Error(data.error||'Não foi possível concluir.');e.status=r.status;e.current=data.current;if(r.status===401&&path!=='/login'){await remember(null);window.dispatchEvent(new Event('pv-access-revoked'))}throw e}
  return data;
 }
 async function login(name,pin,kind){const result=await api('/login',{method:'POST',body:JSON.stringify({name,pin,kind})});await remember(result);return result}
 async function logout(){try{await api('/logout',{method:'POST'})}finally{await remember(null)}}
 async function demands({since}={}){const rows=[];let next={},until;for(let page=0;page<1000;page++){const params=new URLSearchParams({...next,...(since?{since}:{})});const r=await api('/demands'+(params.size?'?'+params:''));rows.push(...r.demands);until=r.until;if(!r.has_more)return {demands:rows,until};next={until,after:r.after,after_id:r.after_id}}throw Error('A sincronização precisa continuar. Tente novamente.');}
 function live(onChange,onStatus=()=>{}){
  const endpoint='wss://ugqxcarzyjmybyrpdrqx.supabase.co/realtime/v1/websocket?apikey=sb_publishable_sgOPJAIJdYl3PPBPh_zIwA_MSss6Scj&vsn=1.0.0';
  let socket,timer,heartbeat,closed=false,attempt=0,ref=0,pending,pendingKind;
  const notify=kind=>{pendingKind=pendingKind&&pendingKind!==kind?"all":kind;clearTimeout(pending);pending=setTimeout(()=>{const k=pendingKind;pendingKind=null;onChange(k)},180)};
  function connect(){
   if(closed)return;onStatus('Conectando…');socket=new WebSocket(endpoint);
   socket.onopen=()=>{socket.send(JSON.stringify({topic:'realtime:pv-production',event:'phx_join',payload:{config:{broadcast:{ack:false,self:false},presence:{enabled:false},private:false}},ref:'join',join_ref:'join'}));heartbeat=setInterval(()=>{if(socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify({topic:'phoenix',event:'heartbeat',payload:{},ref:String(++ref)}))},20000)};
   socket.onmessage=e=>{try{const m=JSON.parse(e.data);if(m.event==='phx_reply'&&m.ref==='join'){if(m.payload.status==='ok'){attempt=0;onStatus('Ao vivo');notify('all')}else socket.close()}if(m.event==='broadcast'&&m.payload?.event==='changed')notify(m.payload.payload?.kind||'all');if(m.event==='phx_error'||m.event==='phx_close')socket.close()}catch{}};
   socket.onerror=()=>socket.close();socket.onclose=()=>{clearInterval(heartbeat);if(closed)return;onStatus('Reconectando…');timer=setTimeout(connect,Math.min(30000,1000*2**attempt++))};
  }
  connect();const fallback=setInterval(()=>notify('all'),30000);
  const online=()=>notify('all');window.addEventListener('online',online);
  return ()=>{closed=true;clearTimeout(timer);clearTimeout(pending);clearInterval(heartbeat);clearInterval(fallback);socket?.close();window.removeEventListener('online',online)};
 }
 window.PVTeam={api,demands,login,logout,live,ready,get member(){return credentials?.member||null}};
})();

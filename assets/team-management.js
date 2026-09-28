const $=s=>document.querySelector(s),escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let sectors=[],members=[],loading=false;
const message=text=>$('#message').textContent=text;
const sectorName=code=>sectors.find(s=>s.codigo===code)?.nome||'Central';
function options(selected=''){return sectors.map(s=>`<option value="${escapeHtml(s.codigo)}" ${s.codigo===selected?'selected':''}>${escapeHtml(s.nome)}</option>`).join('')}
async function refresh(){if(loading)return;loading=true;try{const [s,m]=await Promise.all([PVTeam.api('/manage/sectors'),PVTeam.api('/manage/members')]);sectors=s.sectors;members=m.members;const current=$('#newMember [name=sector_code]').value;$('#newMember [name=sector_code]').innerHTML=options(current);render()}catch(e){message(e.message)}finally{loading=false}}
function render(){
 $('#sectors').innerHTML=sectors.map(s=>`<div class="row"><b>${escapeHtml(s.nome)}</b><div class="actions"><button data-rename="${escapeHtml(s.codigo)}" class="secondary">Renomear</button><button data-remove-sector="${escapeHtml(s.codigo)}" class="danger">Excluir</button></div></div>`).join('')||'<p>Nenhum setor cadastrado.</p>';
 $('#members').innerHTML=members.map(m=>`<div class="row"><div><b>${escapeHtml(m.name)}</b> <span class="tag ${m.active?'':'off'}">${m.active?'Ativo':'Bloqueado'}</span><div class="small">${m.kind==='seller'?'Vendedor / Central':escapeHtml(sectorName(m.sector_code))} · Nome de acesso: ${escapeHtml(m.login_name)}</div></div><div class="actions"><button data-edit="${m.id}">Editar / trocar código</button><button data-toggle="${m.id}" class="secondary">${m.active?'Bloquear':'Reativar'}</button><button data-delete="${m.id}" class="danger">Excluir</button></div></div>`).join('')||'<p>Crie o primeiro acesso no formulário acima.</p>';
}
async function action(fn,success){try{await fn();message(success);await refresh()}catch(e){message(e.message)}}
$('#newSector').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;await action(async()=>{await PVTeam.api('/manage/sectors',{method:'POST',body:JSON.stringify({name:f.elements.name.value})});f.reset()},'Setor adicionado.');b.disabled=false};
$('#newMember [name=kind]').onchange=e=>{const isSector=e.target.value==='sector';$('#sectorLabel').hidden=!isSector;$('#newMember [name=sector_code]').required=isSector};
$('#newMember').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;await action(async()=>{const data=Object.fromEntries(new FormData(f));await PVTeam.api('/manage/members',{method:'POST',body:JSON.stringify(data)});f.elements.pin.value='';f.elements.name.value=''},'Acesso criado. Entregue à pessoa o nome e o código escolhido.');b.disabled=false};
$('#members').onclick=async e=>{const b=e.target.closest('button');if(!b)return;const id=b.dataset.edit||b.dataset.toggle||b.dataset.delete,m=members.find(m=>m.id===id);if(!m)return;
 if(b.dataset.edit){const f=$('#editMember');f.elements.id.value=id;f.elements.name.value=m.name;f.elements.pin.value='';f.elements.sector_code.innerHTML=options(m.sector_code);$('#editSectorLabel').hidden=m.kind!=='sector';$('#editDialog').showModal()}
 if(b.dataset.toggle)await action(()=>PVTeam.api('/manage/members/'+id,{method:'PATCH',body:JSON.stringify({active:!m.active})}),'Acesso atualizado.');
 if(b.dataset.delete&&confirm('Excluir o acesso de '+m.name+'? A extensão dessa pessoa será desconectada.'))await action(()=>PVTeam.api('/manage/members/'+id,{method:'DELETE'}),'Acesso excluído.');
};
$('#editMember').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,m=members.find(x=>x.id===f.elements.id.value),data={name:f.elements.name.value};if(f.elements.pin.value)data.pin=f.elements.pin.value;if(m.kind==='sector')data.sector_code=f.elements.sector_code.value;await action(async()=>{await PVTeam.api('/manage/members/'+m.id,{method:'PATCH',body:JSON.stringify(data)});$('#editDialog').close()},'Acesso alterado. A pessoa deverá entrar novamente.');};
$('#sectors').onclick=async e=>{const b=e.target.closest('button');if(!b)return;const code=b.dataset.rename||b.dataset.removeSector,s=sectors.find(s=>s.codigo===code);if(!s)return;
 if(b.dataset.rename){const name=prompt('Novo nome do setor:',s.nome);if(name&&name!==s.nome)await action(()=>PVTeam.api('/manage/sectors/'+code,{method:'PATCH',body:JSON.stringify({name})}),'Setor renomeado.');}
 if(b.dataset.removeSector){const f=$('#deleteSector');f.elements.code.value=code;f.elements.target.innerHTML='<option value="">Sem transferência (se não houver demandas)</option>'+sectors.filter(s=>s.codigo!==code).map(s=>`<option value="${escapeHtml(s.codigo)}">${escapeHtml(s.nome)}</option>`).join('');$('#deleteDialog').showModal()}
};
$('#deleteSector').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget;await action(async()=>{await PVTeam.api('/manage/sectors/'+f.elements.code.value,{method:'DELETE',body:JSON.stringify({target:f.elements.target.value||null})});$('#deleteDialog').close()},'Setor excluído e acessos bloqueados.');};
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>b.closest('dialog').close());
PVTeam.live(kind=>{if(['access','all'].includes(kind))refresh()},text=>$('#live').textContent=text);refresh();

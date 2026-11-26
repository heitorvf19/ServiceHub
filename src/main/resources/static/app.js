import {
  estado,
  rotas,
  acoes,
  formularios,
  mudancas,
  api,
  ir,
  atualizar,
  h,
  ic,
  botao,
  link,
  cab,
  aviso,
  $,
} from './core.js';
import './contas.js';
import './pedidos.js';
import './servico.js';

let versao = 0,
  anterior = '';
const atual = () => location.hash.slice(1).replace(/^\/+/, '') || 'inicio';
const publicas = new Set(['inicio', 'login', 'cadastro', 'recuperar', 'redefinir']);
function moldura(rota) {
  const u = estado.usuario,
    pro = u?.tipo === 'PROFISSIONAL';
  $('#conta-topo').innerHTML =
    `<div class="top-actions">${u ? `<a class="iconbtn" href="#conversas" aria-label="Conversas">${ic('chat-dots')}</a><a class="who" href="#perfil">${h(u.nome.split(' ')[0])} ${ic('person-circle')}</a>` : link('Entrar', 'login', 'secundario', 'person')}</div>`;
  let grupo = rota.split('/')[0];
  const ativo =
    grupo === 'inicio'
      ? 'inicio'
      : ['perfil', 'login', 'cadastro', 'recuperar', 'redefinir'].includes(grupo)
        ? 'perfil'
        : grupo === 'painel'
          ? rota
          : pro
            ? 'painel/profissional'
            : 'painel/cliente';
  $('#menu').innerHTML = [
    ['Início', 'house', 'inicio'],
    ['Profissional', 'wrench', 'painel/profissional'],
    ['Contratar', 'search', 'painel/cliente'],
    ['Perfil', 'person', 'perfil'],
  ]
    .map(
      ([n, i, to]) =>
        `<a href="#${to}" ${ativo === to ? 'aria-current="page"' : ''}>${ic(i)}${n}</a>`,
    )
    .join('');
}
async function render() {
  const ticket = ++versao,
    rota = atual(),
    [pagina, ...args] = rota.split('/');
  let tela = pagina;
  let html;
  moldura(rota);
  if (!estado.usuario && !publicas.has(pagina)) {
    estado.destino = rota;
    tela = 'login';
  }
  const pro = estado.usuario?.tipo === 'PROFISSIONAL';
  const exigePro =
    ['chamados', 'minhas-propostas'].includes(pagina) || rota === 'painel/profissional';
  const exigeCliente =
    ['nova', 'recebidas', 'editar', 'repetir'].includes(pagina) || rota === 'painel/cliente';
  $('#conteudo').setAttribute('aria-busy', 'true');
  try {
    if (estado.usuario && ((pro && exigeCliente) || (!pro && exigePro))) {
      html = `<div class="narrow">${cab(exigePro ? 'Área do Profissional' : 'Área do Cliente')}<section class="card"><p>Você está conectado como ${pro ? 'profissional' : 'cliente'}. Para acessar esta área, entre com uma conta de ${exigePro ? 'profissional' : 'cliente'}.</p><div class="actions">${botao('Trocar de conta', 'trocar', rota, 'primario')}${link('Minha área', pro ? 'painel/profissional' : 'painel/cliente')}</div></section></div>`;
    } else {
      const fn = rotas[tela];
      if (!fn) throw new Error('Tela não encontrada.');
      html = await fn(args);
    }
  } catch (e) {
    if (ticket !== versao) return;
    if (e.status === 401 && estado.usuario) {
      estado.usuario = null;
      estado.destino = rota;
      return ir('login');
    }
    html = `${cab('Não foi possível abrir a tela')}<p class="error">${h(e.message)}</p>${botao('Tentar novamente', 'recarregar')}`;
  }
  if (ticket !== versao) return;
  $('#conteudo').innerHTML = html;
  $('#conteudo').setAttribute('aria-busy', 'false');
  document.title = ($('#conteudo h1')?.textContent || 'Início') + ' · ServiceHub';
  if (rota !== anterior) {
    window.scrollTo(0, 0);
    $('#conteudo').focus({ preventScroll: true });
  }
  anterior = rota;
}
acoes.recarregar = () => atualizar();
async function executar(el, fn) {
  if (!fn || el.dataset.ocupado) return;
  const target = el.closest('form')?.querySelector('[data-erro]') || $('#conteudo > [data-erro]');
  if (target) target.textContent = '';
  el.dataset.ocupado = '1';
  const controle = el.tagName === 'FORM' ? el.querySelector('[type=submit]') : el;
  if (controle) controle.disabled = true;
  try {
    await fn(el);
  } catch (e) {
    if (e.status === 401 && estado.usuario) {
      estado.usuario = null;
      estado.destino = atual();
      ir('login');
    }
    if (target) target.textContent = e.message;
    else aviso(e.message);
  } finally {
    delete el.dataset.ocupado;
    if (controle) controle.disabled = false;
  }
}
document.addEventListener('click', (e) => {
  const skip = e.target.closest('.skip');
  if (skip) {
    e.preventDefault();
    $('#conteudo').focus();
    return;
  }
  const b = e.target.closest('[data-acao]');
  if (b) {
    e.preventDefault();
    executar(b, acoes[b.dataset.acao]);
  }
});
document.addEventListener('submit', (e) => {
  const f = e.target;
  if (!f.dataset.form) return;
  e.preventDefault();
  executar(f, formularios[f.dataset.form]);
});
document.addEventListener('change', (e) => {
  const el = e.target;
  if (el.dataset.mudar) executar(el, mudancas[el.dataset.mudar]);
});
window.addEventListener('hashchange', render);
window.addEventListener('hub:atualizar', render);
try {
  const [sessao, categorias] = await Promise.all([api('/auth/sessao'), api('/categorias')]);
  estado.usuario = sessao.usuario;
  estado.demo = sessao.demonstracao;
  estado.categorias = categorias;
  await render();
} catch (e) {
  $('#conteudo').innerHTML =
    `<p class="error">${h(e.message)}</p>${botao('Tentar novamente', 'reiniciar')}`;
  acoes.reiniciar = () => location.reload();
}

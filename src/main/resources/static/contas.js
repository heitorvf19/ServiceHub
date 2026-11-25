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
  campo,
  inp,
  area,
  link,
  botao,
  cab,
  vazio,
  msgErro,
  submit,
  dados,
  confirmar,
  aviso,
  reputacao,
  kv,
  iconsCat,
} from './core.js';

const rotaPerfil = () =>
  estado.usuario?.tipo === 'PROFISSIONAL' ? 'painel/profissional' : 'painel/cliente';
const escolhe = (titulo, sub, icone, rota, tipo = '') =>
  `<a class="choice ${tipo}" href="#${rota}"><span class="icon-wrap">${ic(icone)}</span><span><strong>${h(titulo)}</strong><small>${h(sub)}</small></span><span class="end">${ic('chevron-right')}</span></a>`;

rotas.inicio = async () => {
  const m = await api('/comunidade');
  return `<section class="welcome" aria-labelledby="titulo-inicio"><div class="hero"><h1 id="titulo-inicio">Conecte-se com profissionais</h1><p class="lede">Encontre o serviço que precisa ou ofereça suas habilidades na sua região.</p></div>
    <div class="grid2">${escolhe('Área do Profissional', 'Encontre chamados e acompanhe seus trabalhos', 'wrench', 'painel/profissional', 'pro')}${escolhe('Área do Cliente', 'Solicite serviços e compare propostas', 'house-door', 'painel/cliente', 'cli')}</div></section>
    <section class="section"><h2>Categorias populares</h2><div class="categories">${[1, 2, 3, 5]
      .map((id) => {
        const c = estado.categorias.find((x) => x.id === id);
        return c
          ? `<a class="category" href="#nova/${id}">${ic(iconsCat[id])}<strong>${h(c.nome)}</strong><small>Solicitar serviço ${ic('arrow-right')}</small></a>`
          : '';
      })
      .join('')}</div></section>
    <section class="section"><h2>Nossa comunidade</h2><div class="metrics">${[
      [m.profissionais, 'Profissionais'],
      [m.servicosConcluidos, 'Serviços concluídos'],
      [m.media == null ? '—' : Number(m.media).toLocaleString('pt-BR'), 'Avaliação média'],
    ]
      .map(([v, n]) => `<div class="metric"><strong>${h(v)}</strong><small>${n}</small></div>`)
      .join('')}</div></section>`;
};

rotas.login =
  () => `<div class="narrow">${cab('Entrar na conta', 'Acesse seus pedidos, propostas e conversas.')}
 <form class="form-card" data-form="login">${campo('E-mail', inp('email', 'email', '', 'required autocomplete="username"'))}${campo('Senha', inp('senha', 'password', '', 'required autocomplete="current-password" maxlength="128"'))}
 ${msgErro}${submit('Entrar')}<div class="links"><a href="#cadastro">Criar conta</a><a href="#recuperar">Esqueci minha senha</a></div></form>
 ${estado.demo ? `<section class="card section"><h2>Contas de demonstração</h2><div class="actions">${botao('Entrar como cliente', 'demo', 'cliente', 'primario')}${botao('Entrar como profissional', 'demo', 'profissional', 'verde')}</div></section>` : ''}</div>`;

async function entrar(d) {
  estado.usuario = await api('/auth/login', 'POST', d);
  let dest = estado.destino || rotaPerfil();
  if (
    estado.usuario.tipo === 'CLIENTE' &&
    /^(chamados|minhas-propostas|painel\/profissional)/.test(dest)
  )
    dest = rotaPerfil();
  if (estado.usuario.tipo === 'PROFISSIONAL' && /^(nova|recebidas|painel\/cliente)/.test(dest))
    dest = rotaPerfil();
  estado.destino = null;
  estado.rascunho = null;
  ir(dest);
}
formularios.login = (f) => entrar(dados(f));
acoes.demo = (b) => entrar({ email: b.dataset.id + '@servicehub.test', senha: 'ServiceHub123' });
acoes.sair = async () => {
  await api('/auth/logout', 'POST');
  estado.usuario = null;
  estado.rascunho = null;
  estado.destino = null;
  ir('login');
};
acoes.trocar = async (b) => {
  await acoes.sair();
  estado.destino = b.dataset.id;
};
const opcoesCategorias = (lista = []) =>
  `<fieldset><legend>Categorias atendidas</legend><div class="chip-row">${estado.categorias.map((c) => `<label class="check"><input name="categorias" type="checkbox" value="${c.id}" ${lista.some((x) => Number(x.id ?? x) === c.id) ? 'checked' : ''}>${h(c.nome)}</label>`).join('')}</div></fieldset>`;
const profissional = (u = {}) =>
  `${campo('Apresentação', area('apresentacao', u.apresentacao, 'minlength="20" maxlength="1000" required'))}${opcoesCategorias(u.categorias)}<div class="fields two">${campo('Cidade de atendimento', inp('cidade', 'text', u.cidade, 'required maxlength="100"'))}${campo('Bairros atendidos (opcional)', inp('bairros', 'text', u.bairros, 'maxlength="400"'))}</div>`;
rotas.cadastro =
  () => `<div class="narrow">${cab('Criar conta', 'Escolha como deseja usar o ServiceHub.', 'login')}
 <form class="form-card" data-form="cadastro">${campo('Tipo de conta', '<select name="tipo" data-mudar="tipo"><option value="CLIENTE">Cliente</option><option value="PROFISSIONAL">Profissional autônomo</option></select>')}
 ${campo('Nome completo', inp('nome', 'text', '', 'required autocomplete="name" maxlength="100"'))}<div class="fields two">${campo('E-mail', inp('email', 'email', '', 'required autocomplete="email"'))}${campo('Telefone', inp('telefone', 'tel', '', 'required autocomplete="tel"'))}</div>
 <div class="fields two">${campo('Senha', inp('senha', 'password', '', 'required minlength="8" maxlength="128" autocomplete="new-password"'))}${campo('Confirmar senha', inp('confirmacaoSenha', 'password', '', 'required minlength="8" maxlength="128" autocomplete="new-password"'))}</div>
 <div class="fields" id="prof-extra"></div>${msgErro}${submit('Cadastrar')}</form></div>`;
mudancas.tipo = (e) => {
  document.querySelector('#prof-extra').innerHTML =
    e.value === 'PROFISSIONAL' ? profissional() : '';
};
function formPerfil(f) {
  const d = dados(f);
  d.categorias = new FormData(f).getAll('categorias').map(Number);
  return d;
}
formularios.cadastro = async (f) => {
  await api('/auth/cadastro', 'POST', formPerfil(f));
  aviso('Conta criada. Entre com seu e-mail e senha.');
  ir('login');
};
rotas.recuperar =
  () => `<div class="narrow">${cab('Recuperar senha', 'Informe o e-mail usado no cadastro.', 'login')}<form class="form-card" data-form="recuperar">
 ${campo('E-mail', inp('email', 'email', '', 'required autocomplete="email"'))}${msgErro}${submit('Solicitar recuperação')}<small class="help">Na versão local, a mensagem fica na pasta dados/caixa-email-demo do computador que executa o aplicativo.</small></form></div>`;
formularios.recuperar = async (f) => {
  const r = await api('/auth/recuperacao', 'POST', dados(f));
  f.innerHTML = `<p class="notice">${h(r.mensagem)}</p>${link('Voltar ao login', 'login')}`;
};
rotas.redefinir = ([
  codigo,
]) => `<div class="narrow">${cab('Definir nova senha', '', 'login')}<form class="form-card" data-form="redefinir">${inp('codigo', 'hidden', codigo)}
 ${campo('Nova senha', inp('senha', 'password', '', 'required minlength="8" maxlength="128" autocomplete="new-password"'))}${campo('Confirmar senha', inp('confirmacaoSenha', 'password', '', 'required minlength="8" maxlength="128" autocomplete="new-password"'))}${msgErro}${submit('Salvar nova senha')}</form></div>`;
formularios.redefinir = async (f) => {
  await api('/auth/redefinir', 'POST', dados(f));
  estado.usuario = null;
  aviso('Senha atualizada. Entre novamente.');
  ir('login');
};

rotas.perfil = async () => {
  const u = (estado.usuario = await api('/perfil')),
    pro = u.tipo === 'PROFISSIONAL';
  return `<div class="wide-form">${cab('Meu perfil', pro ? 'Profissional autônomo' : 'Cliente', rotaPerfil())}<div class="stack"><section class="card"><div class="row"><div><h2>${h(u.nome)}</h2><p class="muted">${h(u.email)} · ${h(reputacao(u))}</p></div>${botao('Sair da conta', 'sair')}</div></section>
  <form class="form-card" data-form="perfil"><h2>Dados do perfil</h2><div class="fields two">${campo('Nome completo', inp('nome', 'text', u.nome, 'required maxlength="100"'))}${campo('Telefone', inp('telefone', 'tel', u.telefone, 'required'))}</div>${pro ? profissional(u) : ''}${msgErro}${submit('Salvar alterações')}</form>
  ${pro ? `<section class="card"><h2>Conta recebedora</h2><p class="muted">${u.contaVinculada ? 'Vinculada ao provedor simulado.' : 'Vincule sua conta para receber pelos serviços concluídos.'}</p>${u.contaVinculada ? '' : botao('Vincular conta de demonstração', 'vincular', '', 'verde')}</section>` : ''}</div></div>`;
};
formularios.perfil = async (f) => {
  estado.usuario = await api('/perfil', 'PUT', formPerfil(f));
  aviso('Perfil salvo.');
  atualizar();
};
acoes.vincular = async () => {
  if (
    await confirmar(
      'Vincular conta recebedora',
      'O provedor de demonstração criará um identificador fictício para receber pagamentos.',
      'Vincular',
    )
  ) {
    await api('/simulador/conta-recebedora', 'POST', { confirmar: true });
    atualizar();
  }
};
rotas.usuario = async ([id, servico]) => {
  const u = await api('/usuarios/' + Number(id));
  return `<div class="narrow">${cab(u.nome, u.tipo === 'CLIENTE' ? 'Cliente' : 'Profissional autônomo', servico ? 'servico/' + servico : 'inicio')}<div class="stack"><section class="card">${u.apresentacao ? `<p class="pre">${h(u.apresentacao)}</p>` : ''}${kv(
    [
      ['Reputação', reputacao(u)],
      ['Categorias', u.categorias.map((c) => c.nome).join(', ')],
    ],
  )}</section><section><h2>Avaliações publicadas</h2><div class="stack">${u.avaliacoes.length ? u.avaliacoes.map((a) => `<div class="card"><strong>${a.nota} ★ · ${h(a.autor)}</strong><p class="pre">${h(a.comentario)}</p></div>`).join('') : vazio('Sem avaliações')}</div></section></div></div>`;
};

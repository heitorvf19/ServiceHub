import {
  estado,
  rotas,
  acoes,
  formularios,
  api,
  ir,
  atualizar,
  h,
  ic,
  iconsCat,
  etiqueta,
  nome,
  nomes,
  link,
  botao,
  cab,
  vazio,
  campo,
  inp,
  area,
  kv,
  msgErro,
  submit,
  dados,
  op,
  lerFotos,
  instante,
  localInput,
  data,
  dinheiro,
  reputacao,
  aviso,
} from './core.js';

const painel = () => (estado.usuario.tipo === 'CLIENTE' ? 'painel/cliente' : 'painel/profissional');
const fim = (s) => ['CONCLUIDO', 'CANCELADO'].includes(s.estado);
const opcao = (rota, i, t, desc, contador = '') =>
  `<a href="#${rota}" class="card list-card"><div class="row"><span class="icon-wrap">${ic(i)}</span>${contador !== '' ? `<span class="pill">${h(contador)}</span>` : ''}</div><h3>${t}</h3><p class="muted">${desc}</p><span>${ic('arrow-right')}</span></a>`;
rotas.painel = async ([tipo]) => {
  const pro = tipo === 'profissional';
  const [lista, propostas] = await Promise.all([
    api('/servicos'),
    pro ? api('/propostas/minhas') : Promise.resolve([]),
  ]);
  const ativos = lista.filter((s) => !fim(s)),
    anteriores = lista.filter(fim);
  return `${cab(pro ? 'Prestar serviços' : 'Contratar serviços', 'O que você gostaria de fazer?')}<div class="grid2 fluid ${pro ? 'pro' : 'cli'}">
  ${pro ? opcao('chamados', 'search', 'Ver chamados', 'Encontre solicitações nas suas categorias e cidade.') : opcao('nova', 'plus-lg', 'Solicitar novo serviço', 'Descreva o que precisa e receba propostas.')}
  ${
    pro
      ? opcao(
          'minhas-propostas',
          'file-earmark-text',
          'Minhas propostas',
          'Acompanhe as ofertas enviadas e converse com os clientes.',
          propostas.length,
        )
      : opcao(
          'recebidas',
          'clipboard-check',
          'Analisar propostas recebidas',
          'Compare os valores, os profissionais e os termos.',
          lista.reduce((n, s) => n + s.propostasPendentes, 0),
        )
  }
  ${opcao('servicos/ativos', 'clock', pro ? 'Serviços agendados e ativos' : 'Acompanhar serviços ativos', 'Consulte a situação e os próximos passos de cada serviço.', ativos.length)}
  ${opcao('servicos/historico', 'clock-history', 'Histórico de serviços', 'Consulte trabalhos encerrados, pagamentos e avaliações.', anteriores.length)}</div>`;
};
const cardServico = (
  s,
) => `<article class="card list-card"><div class="row"><span class="meta">${ic(iconsCat[s.categoriaId])} ${h(s.categoria)}</span>${etiqueta(s.estado)}</div>
 <h3>${h(s.titulo)}</h3><div class="meta"><span>${ic('geo-alt')}${h(s.bairro)}, ${h(s.cidade)}</span>${s.contrato ? `<span>${ic('person')}${h(estado.usuario.tipo === 'CLIENTE' ? s.contrato.profissional : s.cliente)}</span><span class="amount">${dinheiro(s.contrato.valor)}</span>` : ''}</div>
 <div class="actions">${link('Ver detalhes', 'servico/' + s.id)}${s.estado === 'CONCLUIDO' && estado.usuario.tipo === 'CLIENTE' ? link('Contratar novamente', 'repetir/' + s.id, 'link') : ''}</div></article>`;
rotas.servicos = async ([modo = 'ativos']) => {
  const f = estado.filtrosServicos?.[modo] || {},
    q = new URLSearchParams(Object.entries(f).filter(([, v]) => v));
  const rows = (await api('/servicos?' + q)).filter((s) =>
    modo === 'historico' ? fim(s) : !fim(s),
  );
  const encerrado = modo === 'historico',
    validos = encerrado
      ? ['CONCLUIDO', 'CANCELADO']
      : ['ABERTA', 'CONTRATADO', 'AGENDADO', 'EM_ANDAMENTO', 'AGUARDANDO_CONFIRMACAO'];
  const total = rows.reduce(
    (a, s) => a + (s.pagamento?.estado === 'PAGO' ? Number(s.pagamento.valor) : 0),
    0,
  );
  return `${cab(encerrado ? 'Histórico de serviços' : 'Serviços ativos', '', painel())}<div class="tabs"><a class="${!encerrado ? 'active' : ''}" href="#servicos/ativos">Ativos</a><a class="${encerrado ? 'active' : ''}" href="#servicos/historico">Histórico</a></div>
  <form data-form="filtroServicos" data-modo="${modo}" class="form-card"><div class="grid3">${campo('Situação', `<select name="estado"><option value="">Todas</option>${validos.map((v) => `<option value="${v}" ${f.estado === v ? 'selected' : ''}>${nome(v)}</option>`).join('')}</select>`)}${campo('Atualizado de', inp('de', 'date', f.de))}${campo('Atualizado até', inp('ate', 'date', f.ate))}</div>${msgErro}${submit('Filtrar', 'secundario')}</form>
  <section class="section"><div class="row"><h2>${rows.length} serviço(s)</h2>${encerrado ? `<span class="pill green">${estado.usuario.tipo === 'CLIENTE' ? 'Total pago' : 'Total recebido'} · ${dinheiro(total)}</span>` : ''}</div><div class="grid2 fluid">${rows.length ? rows.map(cardServico).join('') : vazio('Nenhum serviço neste filtro.')}</div></section>`;
};
formularios.filtroServicos = (f) => {
  const v = dados(f);
  if (v.de && v.ate && v.de > v.ate)
    throw new Error('A data final deve ser igual ou posterior à inicial.');
  (estado.filtrosServicos ??= {})[f.dataset.modo] = v;
  atualizar();
};
rotas.recebidas = async () => {
  const lista = (await api('/servicos')).filter(
    (s) => s.propostasPendentes > 0 && s.estado === 'ABERTA',
  );
  return `${cab('Propostas recebidas', 'Selecione uma solicitação para comparar as propostas pendentes.', painel())}<div class="grid2 fluid">${lista.length ? lista.map((s) => `<article class="card"><span class="pill blue">${s.propostasPendentes} pendente(s)</span><h3>${h(s.titulo)}</h3><p class="muted">${h(s.categoria)} · ${h(s.bairro)}</p>${link('Comparar propostas', 'comparar/' + s.id, 'primario')}</article>`).join('') : vazio('Nenhuma proposta pendente.', 'Novas propostas aparecerão aqui.')}</div>`;
};
rotas.chamados = async () => {
  const f = estado.busca || {},
    q = new URLSearchParams(Object.entries(f).filter(([, v]) => v));
  const rows = await api('/oportunidades?' + q),
    limite = estado.limite || 8;
  return `${cab('Chamados', 'Solicitações abertas nas suas categorias e cidade de atendimento.', painel())}
  <form class="form-card" data-form="busca"><div class="grid3">${campo('Pesquisar por serviço', inp('texto', 'search', f.texto, 'placeholder="Título ou descrição"'))}
  ${campo('Categoria', `<select name="categoria"><option value="">Todas</option>${estado.usuario.categorias.map((c) => `<option value="${c.id}" ${String(c.id) === f.categoria ? 'selected' : ''}>${h(c.nome)}</option>`).join('')}</select>`)}
  ${campo('Bairro', inp('bairro', 'search', f.bairro))}</div>${submit('Buscar chamados', 'verde')}</form>
  <section class="section"><h2>${rows.length} chamado(s)</h2><div class="grid2 fluid">${
    rows
      .slice(0, limite)
      .map(
        (s) =>
          `<article class="card list-card"><span class="pill">${h(s.categoria)}</span><h3>${h(s.titulo)}</h3><p class="muted pre">${h(s.descricao)}</p><div class="meta"><span>${ic('geo-alt')} ${h(s.bairro)}, ${h(s.cidade)}</span><span>${h(s.cliente)} · ${h(reputacao(s.reputacaoCliente))}</span></div><div class="actions">${link('Ver detalhes', 'servico/' + s.id)}</div></article>`,
      )
      .join('') ||
    vazio('Nenhum chamado encontrado.', 'Confira os filtros ou sua cidade e categorias no perfil.')
  }</div>${rows.length > limite ? botao('Carregar mais chamados', 'mais') : ''}</section>`;
};
formularios.busca = (f) => {
  estado.busca = dados(f);
  estado.limite = 8;
  atualizar();
};
acoes.mais = () => {
  estado.limite = (estado.limite || 8) + 8;
  atualizar();
};
rotas['minhas-propostas'] = async () => {
  const lista = await api('/propostas/minhas');
  return `${cab('Minhas propostas', 'Consulte o valor, o status e as conversas de cada oferta.', painel())}<div class="grid2 fluid">${lista.length ? lista.map((p) => `<article class="card"><div class="row"><span class="pill">${h(p.categoria)}</span>${etiqueta(p.estado)}</div><h3>${h(p.titulo)}</h3><p class="amount">${dinheiro(p.valor)} · ${h(p.duracaoHoras)} h</p><p class="muted pre">${h(p.escopo)}</p>${link('Ver solicitação', 'servico/' + p.servicoId)}</article>`).join('') : vazio('Você ainda não enviou propostas.')}</div>`;
};

function iniciar(origem, base = {}, categoria) {
  estado.rascunho = {
    origem,
    passo: categoria ? 2 : 1,
    chave: op(),
    categoriaId: categoria || base.categoriaId || null,
    titulo: base.titulo || '',
    descricao: base.descricao || '',
    cidade: base.cidade || '',
    bairro: base.bairro || '',
    endereco: base.endereco || '',
    preferencias: base.preferencias || '',
    frequencia: base.frequencia || 'Uma vez',
    dataPreferida: base.dataPreferida || null,
    orcamentoMin: base.orcamentoMin ?? '',
    orcamentoMax: base.orcamentoMax ?? '',
    fotos: null,
    antigas: base.fotos || [],
  };
}
rotas.nova = async ([categoria]) => {
  const origem = 'nova/' + (categoria || '');
  if (estado.rascunho?.origem !== origem)
    iniciar(
      origem,
      {},
      estado.categorias.some((c) => c.id === Number(categoria)) ? Number(categoria) : null,
    );
  return wizard();
};
for (const modo of ['editar', 'repetir'])
  rotas[modo] = async ([id]) => {
    const origem = modo + '/' + Number(id);
    if (estado.rascunho?.origem !== origem) {
      const base = await api('/servicos/' + Number(id));
      if (modo === 'repetir') {
        base.dataPreferida = null;
        base.fotos = [];
      }
      iniciar(origem, base);
      estado.rascunho.idOrigem = Number(id);
    }
    return wizard();
  };
const nomesPassos = ['Categoria', 'Personalização', 'Preferências', 'Revisão'];
function wizard() {
  const d = estado.rascunho,
    passo = d.passo,
    categoria = estado.categorias.find((c) => c.id === d.categoriaId);
  const titulo = d.origem.startsWith('editar/') ? 'Editar solicitação' : 'Solicitar serviço';
  let meio = '';
  if (passo === 1)
    meio = `<h2>Escolha a categoria</h2><div class="categories">${estado.categorias.map((c) => `<button type="button" data-acao="categoria" data-id="${c.id}" aria-pressed="${d.categoriaId === c.id}" class="btn category select ${d.categoriaId === c.id ? 'selected' : ''}">${ic(iconsCat[c.id])}${h(c.nome)}</button>`).join('')}</div>`;
  if (passo === 2)
    meio = `<h2>Personalize seu serviço</h2><span class="pill fit">${h(categoria?.nome)}</span>
    ${campo('Título do serviço', inp('titulo', 'text', d.titulo, 'required minlength="5" maxlength="100" placeholder="Ex.: Instalação de chuveiro"'))}
    ${campo('Descreva o que você precisa', area('descricao', d.descricao, 'required minlength="20" maxlength="2000" placeholder="Inclua os detalhes que o profissional precisa saber."'))}
    ${campo('Fotos do serviço (opcional)', inp('fotos', 'file', '', 'accept="image/jpeg,image/png,image/webp" multiple'))}<small class="help">Até 3 fotos. ${d.fotos?.length || d.antigas.length ? `${d.fotos?.length ?? d.antigas.length} foto(s) salva(s) neste pedido.` : ''}</small>`;
  if (passo === 3)
    meio = `<h2>Preferências e localização</h2><div class="fields two">${campo('Cidade', inp('cidade', 'text', d.cidade, 'required maxlength="100"'))}${campo('Bairro', inp('bairro', 'text', d.bairro, 'required maxlength="100"'))}</div>
    ${campo('Endereço completo', inp('endereco', 'text', d.endereco, 'required maxlength="300" autocomplete="street-address"'))}<small class="help">O endereço completo é mostrado ao profissional contratado.</small>
    ${campo('Preferências (opcional)', area('preferencias', d.preferencias, 'maxlength="1000"'))}<div class="fields two">${campo('Data e hora preferidas · São Paulo', inp('dataPreferida', 'datetime-local', localInput(d.dataPreferida)))}
    ${campo('Frequência', `<select name="frequencia">${['Uma vez', 'Semanal', 'Quinzenal', 'Mensal'].map((v) => `<option ${v === d.frequencia ? 'selected' : ''}>${v}</option>`).join('')}</select>`)}</div>
    <div class="fields two">${campo('Orçamento mínimo em R$ (opcional)', inp('orcamentoMin', 'number', d.orcamentoMin, 'min="0" step=".01"'))}${campo('Orçamento máximo em R$ (opcional)', inp('orcamentoMax', 'number', d.orcamentoMax, 'min="0" step=".01"'))}</div>`;
  if (passo === 4)
    meio = `<h2>Revise a solicitação</h2><h3>${h(d.titulo)}</h3><p class="pre">${h(d.descricao)}</p>${kv(
      [
        ['Categoria', categoria?.nome],
        ['Endereço', [d.endereco, d.bairro, d.cidade].join(' · ')],
        ['Data preferida', data(d.dataPreferida)],
        ['Frequência', d.frequencia],
        ['Preferências', d.preferencias],
        ['Orçamento mínimo', d.orcamentoMin !== '' ? dinheiro(d.orcamentoMin) : ''],
        ['Orçamento máximo', d.orcamentoMax !== '' ? dinheiro(d.orcamentoMax) : ''],
        ['Fotos', d.fotos?.length ?? d.antigas.length],
      ],
    )}`;
  return `<div class="wide-form">${cab(titulo, `Passo ${passo} de 4`, painel())}<div class="progress">${nomesPassos.map((n, i) => `<span class="${i < passo ? 'done' : ''}">${h(n)}</span>`).join('')}</div>
    <form class="form-card" data-form="wizard">${meio}${msgErro}<div class="actions">${passo > 1 ? botao('Anterior', 'anterior') : ''}${submit(passo === 4 ? (d.origem.startsWith('editar/') ? 'Salvar alterações' : 'Publicar solicitação') : 'Continuar')}</div></form></div>`;
}
acoes.categoria = (b) => {
  estado.rascunho.categoriaId = Number(b.dataset.id);
  atualizar();
};
async function guardar(f) {
  const d = estado.rascunho,
    v = dados(f);
  if (d.passo === 2) {
    d.titulo = v.titulo;
    d.descricao = v.descricao;
    if (f.elements.fotos.files.length) d.fotos = await lerFotos(f.elements.fotos);
  }
  if (d.passo === 3) {
    Object.assign(d, v);
    d.dataPreferida = instante(v.dataPreferida);
  }
}
acoes.anterior = async (b) => {
  await guardar(b.closest('form'));
  estado.rascunho.passo--;
  atualizar();
};
formularios.wizard = async (f) => {
  const d = estado.rascunho;
  await guardar(f);
  if (d.passo === 1 && !d.categoriaId) throw new Error('Selecione uma categoria para continuar.');
  if (
    d.passo === 3 &&
    d.orcamentoMin !== '' &&
    d.orcamentoMax !== '' &&
    Number(d.orcamentoMin) > Number(d.orcamentoMax)
  )
    throw new Error('O orçamento máximo deve cobrir o mínimo.');
  if (d.passo < 4) {
    d.passo++;
    atualizar();
    return;
  }
  const {
    titulo,
    descricao,
    cidade,
    bairro,
    endereco,
    categoriaId,
    preferencias,
    frequencia,
    dataPreferida,
    fotos,
  } = d;
  const body = {
    titulo,
    descricao,
    cidade,
    bairro,
    endereco,
    categoriaId,
    preferencias,
    frequencia,
    dataPreferida,
    fotos,
    orcamentoMin: d.orcamentoMin === '' ? null : Number(d.orcamentoMin),
    orcamentoMax: d.orcamentoMax === '' ? null : Number(d.orcamentoMax),
  };
  let id = d.idOrigem;
  if (d.origem.startsWith('editar/')) {
    await api('/servicos/' + id, 'PUT', body);
    aviso('Solicitação atualizada.');
  } else {
    id = (await api('/servicos', 'POST', body, { 'Idempotency-Key': d.chave })).id;
    aviso('Solicitação publicada. Aguardando propostas.');
  }
  estado.rascunho = null;
  ir('servico/' + id);
};

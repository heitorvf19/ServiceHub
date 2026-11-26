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
  data,
  dinheiro,
  reputacao,
  etiqueta,
  nome,
  aviso,
  confirmar,
  $,
} from './core.js';

const base = (id) => '/servicos/' + Number(id);
const rota = (id) => 'servico/' + Number(id);
const cartao = (titulo, html) =>
  `<section class="card panel-body"><h2>${h(titulo)}</h2>${html}</section>`;
const terminal = (s) => ['CONCLUIDO', 'CANCELADO'].includes(s);
const operacao = (el) => el.dataset.operacao || (el.dataset.operacao = op());
const iniciais = (n) =>
  (n || '')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((x) => x[0])
    .join('');
const pessoa = (n, sub) =>
  `<div class="person"><span class="avatar">${h(iniciais(n))}</span><div><strong>${h(n)}</strong><div class="help">${h(sub)}</div></div></div>`;
const descricaoEventos = {
  PUBLICACAO: 'Solicitação publicada',
  EDICAO: 'Solicitação editada',
  CONTRATACAO: 'Proposta contratada',
  HORARIO_PROPOSTO: 'Horário proposto',
  HORARIO_CONFIRMADO: 'Agendamento confirmado',
  HORARIO_RECUSADO: 'Horário recusado',
  HORARIO_INVALIDADO: 'Horário invalidado',
  INICIO: 'Execução iniciada',
  TERMINO: 'Término informado',
  AJUSTE: 'Ajuste solicitado',
  CONCLUSAO: 'Conclusão confirmada',
  CANCELAMENTO: 'Serviço cancelado',
  COBRANCA_INICIADA: 'Cobrança iniciada',
  PAGAMENTO_PAGO: 'Pagamento confirmado',
  PAGAMENTO_RECUSADO: 'Pagamento recusado',
  PAGAMENTO_EXPIRADO: 'Cobrança expirada',
};
const itens = [
  'Avaliar o local e identificar o problema',
  'Reunir ferramentas e materiais necessários',
  'Executar o serviço conforme acordado',
  'Testar e verificar o funcionamento',
  'Limpar a área de trabalho',
];
function fotos(s, etapa) {
  const lista = s.fotos.filter((f) => f.etapa === etapa);
  return lista.length
    ? `<div class="photos">${lista.map((f, i) => `<a target="_blank" rel="noopener" href="/api/servicos/${s.id}/fotos/${f.id}"><img src="/api/servicos/${s.id}/fotos/${f.id}" alt="Foto ${i + 1} ${etapa === 'PEDIDO' ? 'da solicitação' : 'da execução'}"></a>`).join('')}</div>`
    : '';
}
function propostaCard(p, id) {
  return `<article class="card panel-body"><div class="row">${pessoa(p.profissional, reputacao(p.reputacao))}${etiqueta(p.estado)}</div>
  <strong class="price">${dinheiro(p.valor)}</strong><p class="pre">${h(p.escopo)}</p>${kv([
    ['Duração estimada', p.duracaoHoras + ' h'],
    ['Proposta enviada', data(p.criadaEm)],
  ])}
  <div class="actions">${link('Ver perfil', 'usuario/' + p.profissionalId + '/' + id)}${p.estado === 'PENDENTE' ? link('Selecionar proposta', 'termos/' + id + '/' + p.id, 'primario') : ''}</div></article>`;
}
rotas.comparar = async ([id]) => {
  const [s, all] = await Promise.all([api(base(id)), api(base(id) + '/propostas')]);
  const lista = all.filter((p) => p.estado === 'PENDENTE');
  return `${cab('Comparar propostas', s.titulo, rota(id))}<div class="grid2 fluid">${lista.length ? lista.map((p) => propostaCard(p, s.id)).join('') : vazio('Nenhuma proposta pendente.')}</div>`;
};
rotas.termos = async ([id, pid]) => {
  const [s, all] = await Promise.all([api(base(id)), api(base(id) + '/propostas')]);
  const p = all.find((x) => x.id === Number(pid));
  if (!p) throw new Error('Proposta não encontrada.');
  return `<div class="narrow">${cab('Confirmar contratação', s.titulo, 'comparar/' + s.id)}<section class="card panel-body">${pessoa(p.profissional, reputacao(p.reputacao))}
    ${kv([
      ['Valor contratado', dinheiro(p.valor)],
      ['Duração estimada', p.duracaoHoras + ' h'],
    ])}<div><h3>Escopo da proposta</h3><p class="pre">${h(p.escopo)}</p></div>
    <p class="help">O pagamento será habilitado após a conclusão do serviço.</p>${p.estado === 'PENDENTE' && s.estado === 'ABERTA' ? botao('Confirmar contratação', 'contratar', s.id, 'primario', `data-proposta="${p.id}" data-valor="${h(dinheiro(p.valor))}"`) : etiqueta(p.estado)}</section></div>`;
};
acoes.contratar = async (b) => {
  if (
    !(await confirmar(
      'Contratar esta proposta?',
      `O valor acordado é ${b.dataset.valor}. As demais propostas pendentes serão encerradas.`,
      'Contratar',
    ))
  )
    return;
  await api(base(b.dataset.id) + '/propostas/' + b.dataset.proposta + '/contratacao', 'POST', {
    confirmar: true,
    operacaoId: operacao(b),
  });
  aviso('Contrato registrado. Já é possível propor um horário.');
  ir(rota(b.dataset.id));
};

function horarios(s) {
  if (!s.contrato) return '';
  const atual = s.horarios.find((x) => x.estado === 'CONFIRMADO'),
    pendente = s.horarios.find((x) => x.estado === 'PENDENTE');
  const editavel = ['CONTRATADO', 'AGENDADO'].includes(s.estado);
  let html = atual
    ? kv([
        ['Início confirmado', data(atual.inicio)],
        ['Término previsto', data(atual.fim)],
      ])
    : '<p class="muted">Nenhum horário confirmado.</p>';
  if (pendente && editavel) {
    html += `<div class="notice"><strong>Proposta de horário pendente</strong><p>${data(pendente.inicio)} até ${data(pendente.fim)}</p>
      ${
        pendente.autorId === estado.usuario.id
          ? 'Aguardando a outra parte responder.'
          : `<div class="actions">${botao('Aceitar horário', 'horario', s.id, 'verde', `data-horario="${pendente.id}" data-aceitar="true"`)}${botao('Recusar horário', 'horario', s.id, 'secundario', `data-horario="${pendente.id}" data-aceitar="false"`)}</div>`
      }</div>`;
  } else if (editavel) {
    html += `<form class="fields" data-form="horario" data-id="${s.id}"><div class="fields two">${campo('Início · São Paulo', inp('inicio', 'datetime-local', '', 'required'))}${campo('Término · São Paulo', inp('fim', 'datetime-local', '', 'required'))}</div>${msgErro}${submit(atual ? 'Propor reagendamento' : 'Propor horário')}</form>`;
  }
  return cartao('Agendamento', html);
}
function execucao(s, cliente, selecionado) {
  let html = '';
  if (s.estado === 'AGENDADO' && selecionado)
    html = botao('Iniciar serviço', 'inicio', s.id, 'verde');
  if (s.estado === 'EM_ANDAMENTO') {
    html = `${kv([['Início real', data(s.inicioReal)]])}<p class="muted">Tempo desde o início · <strong data-cronometro="${h(s.inicioReal)}"></strong></p>
    <fieldset><legend>Checklist do serviço</legend>${itens.map((t, i) => `<label class="check"><input type="checkbox" data-id="${s.id}" data-item="${i + 1}" ${selecionado ? 'data-mudar="item"' : 'disabled'} ${s.checklist.some((v) => v.item === i + 1) ? 'checked' : ''}>${h(t)}</label>`).join('')}</fieldset>${fotos(s, 'EXECUCAO')}`;
    const ajuste = s.historico.filter((v) => v.evento === 'AJUSTE').at(-1);
    if (ajuste)
      html += `<p class="notice pre"><strong>Ajuste solicitado</strong><br>${h(ajuste.detalhe)}</p>`;
    if (selecionado)
      html += `<form class="fields" data-form="fotoExecucao" data-id="${s.id}">${campo('Fotos da execução', inp('fotos', 'file', '', 'required multiple accept="image/jpeg,image/png,image/webp"'))}${msgErro}${submit('Adicionar fotos', 'secundario')}</form>
      <form class="fields" data-form="termino" data-id="${s.id}">${campo('Relato do que foi realizado', area('texto', '', 'required minlength="10" maxlength="1000"'))}${msgErro}${submit('Informar término', 'verde')}</form>`;
  }
  if (s.estado === 'AGUARDANDO_CONFIRMACAO') {
    const relato = s.historico.filter((v) => v.evento === 'TERMINO').at(-1);
    html = `<div><h3>Relato do profissional</h3><p class="pre">${h(relato?.detalhe)}</p></div>${fotos(s, 'EXECUCAO')}`;
    if (cliente)
      html += `${botao('Confirmar conclusão', 'conclusao', s.id, 'verde')}<details><summary>Solicitar ajuste</summary><form data-form="ajuste" class="fields" data-id="${s.id}">${campo('Descreva o ajuste', area('texto', '', 'required minlength="10" maxlength="1000"'))}${msgErro}${submit('Enviar ajuste', 'secundario')}</form></details>`;
    else html += '<p class="notice">Aguardando o cliente confirmar a conclusão.</p>';
  }
  return html ? cartao('Execução do serviço', html) : '';
}
function avaliacao(s) {
  if (s.estado !== 'CONCLUIDO') return '';
  let html = '';
  if (s.minhaAvaliacao)
    html = `<p class="notice">Sua nota registrada · ${s.minhaAvaliacao.nota} de 5</p>`;
  else if (Date.now() < Date.parse(s.concluidaEm) + 7 * 86400000) {
    html = `<form data-form="avaliar" class="fields" data-id="${s.id}">${campo('Nota', `<select name="nota" required><option value="">Escolha uma nota</option>${[1, 2, 3, 4, 5].map((v) => `<option value="${v}">${v} de 5</option>`).join('')}</select>`)}${campo('Comentário (opcional)', area('comentario', '', 'maxlength="500"'))}${msgErro}${submit('Enviar avaliação')}</form>`;
  } else html = '<p class="muted">Prazo de avaliação encerrado.</p>';
  if (s.avaliacoesPublicadas.length)
    html += s.avaliacoesPublicadas
      .map(
        (a) =>
          `<div><strong>${a.autorId === estado.usuario.id ? 'Sua avaliação' : 'Avaliação recebida'} · ${a.nota} ★</strong><p class="pre">${h(a.comentario)}</p></div>`,
      )
      .join('');
  else
    html +=
      '<small class="help">As notas se tornam públicas quando ambos avaliam ou após sete dias da conclusão.</small>';
  return cartao('Avaliações', html);
}
rotas.servico = async ([id]) => {
  id = Number(id);
  const s = await api(base(id)),
    uid = estado.usuario.id;
  const cliente = s.clienteId === uid,
    selecionado = s.contrato?.profissionalId === uid,
    participante = cliente || selecionado;
  const [ofertas, chats] = await Promise.all([
    cliente ? api(base(id) + '/propostas') : api('/propostas/minhas'),
    api('/conversas'),
  ]);
  const proprias = ofertas.filter((p) => p.servicoId === id),
    pendentes = proprias.filter((p) => p.estado === 'PENDENTE');
  const conversas = chats.filter((c) => c.servicoId === id);
  const d = s.endereco
    ? [s.endereco, s.bairro, s.cidade].join(' · ')
    : [s.bairro, s.cidade].join(' · ');
  let principal = cartao(
    'Detalhes do serviço',
    `<div class="row"><span class="pill">${h(s.categoria)}</span>${etiqueta(s.estado)}</div><p class="pre">${h(s.descricao)}</p>${fotos(s, 'PEDIDO')}
    ${kv([
      ['Localização', d],
      ['Data preferida', s.dataPreferida ? data(s.dataPreferida) : 'A combinar'],
      ['Frequência', s.frequencia],
      ['Preferências', s.preferencias],
      ['Orçamento mínimo', s.orcamentoMin != null ? dinheiro(s.orcamentoMin) : ''],
      ['Orçamento máximo', s.orcamentoMax != null ? dinheiro(s.orcamentoMax) : ''],
    ])}
    ${s.endereco ? `<a href="https://www.google.com/maps/search/?api=1&amp;query=${encodeURIComponent(d)}" rel="noopener noreferrer" target="_blank">${ic('geo-alt')} Abrir no Google Maps</a>` : ''}
    ${!cliente ? link('Perfil de ' + s.cliente, 'usuario/' + s.clienteId + '/' + id, 'link', 'person') : ''}`,
  );
  if (cliente && s.estado === 'ABERTA') {
    principal += cartao(
      'Propostas recebidas',
      pendentes.length
        ? `<p>${pendentes.length} proposta(s) aguardando sua análise.</p>${link('Comparar propostas', 'comparar/' + id, 'primario')}`
        : `<p class="muted">Aguardando propostas dos profissionais.</p>${proprias.length ? '' : link('Editar solicitação', 'editar/' + id)}`,
    );
  }
  if (!cliente && !selecionado) {
    if (proprias.length)
      principal += cartao(
        'Suas propostas',
        proprias
          .map(
            (p) =>
              `<div class="row"><span>${dinheiro(p.valor)} · ${h(p.duracaoHoras)} h</span>${etiqueta(p.estado)}</div>${p.estado === 'PENDENTE' ? botao('Retirar proposta', 'retirar', id, 'secundario', `data-proposta="${p.id}"`) : ''}`,
          )
          .join(''),
      );
    if (s.estado === 'ABERTA' && !pendentes.length)
      principal += cartao(
        'Propor preço',
        `<form class="fields" data-form="oferta" data-id="${id}"><div class="fields two">${campo('Valor total em R$', inp('valor', 'number', '', 'required min=".01" step=".01"'))}${campo('Duração estimada em horas', inp('duracaoHoras', 'number', '', 'required min=".01" step=".01"'))}</div>${campo('Escopo da proposta', area('escopo', '', 'required minlength="20" maxlength="2000"'))}${msgErro}${submit('Enviar proposta', 'verde')}</form>`,
      );
  }
  if (participante && s.contrato)
    principal += cartao(
      'Contrato',
      `${pessoa(s.contrato.profissional, 'Profissional contratado')}${kv([
        ['Valor acordado', dinheiro(s.contrato.valor)],
        ['Duração estimada', s.contrato.duracaoHoras + ' h'],
      ])}<p class="pre">${h(s.contrato.escopo)}</p>${cliente ? link('Perfil do profissional', 'usuario/' + s.contrato.profissionalId + '/' + id, 'link') : ''}`,
    );
  let lateral = '';
  if (participante) {
    lateral += horarios(s) + execucao(s, cliente, selecionado);
    if (s.pagamento)
      lateral += cartao(
        'Pagamento',
        `<div class="row"><strong class="price">${dinheiro(s.pagamento.valor)}</strong>${etiqueta(s.pagamento.estado)}</div>${link('Ver pagamento', 'pagamento/' + id, 'primario')}`,
      );
    lateral += avaliacao(s);
  }
  if (conversas.length || s.telefoneParticipante)
    lateral += cartao(
      'Contato',
      `<div class="actions">${conversas.map((c) => link('Chat com ' + (cliente ? c.profissional : c.cliente), 'conversa/' + c.id, 'secundario', 'chat-dots')).join('')}${s.telefoneParticipante ? `<a class="btn secundario" href="tel:${h(s.telefoneParticipante.replace(/[^+0-9]/g, ''))}">${ic('telephone')} Ligar</a>` : ''}</div>`,
    );
  if (participante) {
    principal += cartao(
      'Histórico do serviço',
      `<ol class="history">${s.historico.map((v) => `<li><strong>${h(descricaoEventos[v.evento] || v.evento)}</strong><small>${data(v.dataHora)} · ${h(v.autor || 'Provedor simulado')}</small>${v.detalhe ? `<p class="pre">${h(v.detalhe)}</p>` : ''}</li>`).join('')}</ol>`,
    );
    if (['ABERTA', 'CONTRATADO', 'AGENDADO'].includes(s.estado))
      lateral += cartao(
        'Cancelamento',
        `<details><summary>Cancelar serviço</summary><form class="fields" data-form="cancelar" data-id="${id}">${campo('Motivo do cancelamento', area('motivo', '', 'required minlength="10" maxlength="500"'))}${msgErro}${submit('Confirmar cancelamento', 'perigo')}</form></details>`,
      );
    if (s.estado === 'CONCLUIDO' && cliente)
      lateral += link('Contratar novamente', 'repetir/' + id, 'secundario', 'arrow-repeat');
  }
  return `${cab(s.titulo, '', participante ? 'servicos/' + (terminal(s.estado) ? 'historico' : 'ativos') : 'chamados')}<div class="${lateral ? 'split' : 'wide-form'}"><div class="stack">${principal}</div><div class="stack">${lateral}</div></div>`;
};

formularios.oferta = async (f) => {
  const d = dados(f);
  await api(base(f.dataset.id) + '/propostas', 'POST', {
    valor: Number(d.valor),
    duracaoHoras: Number(d.duracaoHoras),
    escopo: d.escopo,
  });
  aviso('Proposta enviada. O chat com o cliente está disponível.');
  atualizar();
};
acoes.retirar = async (b) => {
  if (
    await confirmar(
      'Retirar proposta?',
      'Você poderá enviar outra enquanto a solicitação estiver aberta.',
      'Retirar',
    )
  ) {
    await api(base(b.dataset.id) + '/propostas/' + b.dataset.proposta + '/retirada', 'POST', {
      confirmar: true,
    });
    atualizar();
  }
};
formularios.horario = async (f) => {
  const d = dados(f);
  await api(base(f.dataset.id) + '/horarios', 'POST', {
    inicio: instante(d.inicio),
    fim: instante(d.fim),
  });
  aviso('Horário enviado para a outra parte.');
  atualizar();
};
acoes.horario = async (b) => {
  const r = await api(base(b.dataset.id) + '/horarios/' + b.dataset.horario + '/resposta', 'POST', {
    aceitar: b.dataset.aceitar === 'true',
  });
  aviso(r.mensagem);
  atualizar();
};
acoes.inicio = async (b) => {
  if (await confirmar('Iniciar serviço?', 'O horário real de início será registrado.', 'Iniciar')) {
    await api(base(b.dataset.id) + '/inicio', 'POST', { confirmar: true, operacaoId: operacao(b) });
    atualizar();
  }
};
mudancas.item = async (e) => {
  try {
    await api(base(e.dataset.id) + '/checklist/' + e.dataset.item, 'PUT', { marcado: e.checked });
  } catch (err) {
    e.checked = !e.checked;
    throw err;
  }
};
formularios.fotoExecucao = async (f) => {
  await api(base(f.dataset.id) + '/fotos', 'POST', { fotos: await lerFotos(f.elements.fotos) });
  aviso('Fotos adicionadas.');
  atualizar();
};
formularios.termino = async (f) => {
  if (
    await confirmar(
      'Informar término?',
      'O cliente poderá confirmar o serviço ou solicitar um ajuste.',
      'Enviar relato',
    )
  ) {
    await api(base(f.dataset.id) + '/termino', 'POST', {
      texto: dados(f).texto,
      confirmar: true,
      operacaoId: operacao(f),
    });
    atualizar();
  }
};
formularios.ajuste = async (f) => {
  await api(base(f.dataset.id) + '/ajustes', 'POST', {
    texto: dados(f).texto,
    operacaoId: operacao(f),
  });
  aviso('Ajuste solicitado.');
  atualizar();
};
acoes.conclusao = async (b) => {
  if (
    await confirmar(
      'Confirmar conclusão?',
      'O pagamento e as avaliações ficarão disponíveis.',
      'Concluir serviço',
    )
  ) {
    await api(base(b.dataset.id) + '/conclusao', 'POST', {
      confirmar: true,
      operacaoId: operacao(b),
    });
    atualizar();
  }
};
formularios.cancelar = async (f) => {
  if (
    await confirmar(
      'Cancelar serviço?',
      'O cancelamento preserva o histórico e libera o horário reservado.',
      'Cancelar serviço',
    )
  ) {
    await api(base(f.dataset.id) + '/cancelamento', 'POST', {
      motivo: dados(f).motivo,
      confirmar: true,
      operacaoId: operacao(f),
    });
    atualizar();
  }
};
formularios.avaliar = async (f) => {
  const d = dados(f);
  if (
    await confirmar(
      'Enviar avaliação?',
      `Sua nota é ${d.nota} de 5. A avaliação é definitiva.`,
      'Enviar',
    )
  ) {
    await api(base(f.dataset.id) + '/avaliacoes', 'POST', {
      nota: Number(d.nota),
      comentario: d.comentario,
    });
    atualizar();
  }
};

rotas.pagamento = async ([id]) => {
  const [s, p] = await Promise.all([api(base(id)), api(base(id) + '/pagamento')]);
  const cliente = s.clienteId === estado.usuario.id,
    t = p.tentativas[0];
  let acao = '';
  if (cliente && p.estado !== 'PAGO')
    acao =
      p.estado === 'EM_PROCESSAMENTO'
        ? link('Abrir provedor simulado', 'checkout/' + s.id, 'primario')
        : botao('Prosseguir para pagamento', 'cobrar', s.id, 'primario');
  const historico = p.tentativas
    .map(
      (t) =>
        `<div><div class="row"><strong>${dinheiro(t.valor)}</strong>${etiqueta(t.estado)}</div><small class="help">${data(t.criadaEm)} · ${h(t.referencia)}</small></div>`,
    )
    .join('<hr class="sep">');
  return `<div class="narrow">${cab('Pagamento', s.titulo, rota(id))}<div class="stack">${cartao(
    p.estado === 'PAGO' ? 'Comprovante da simulação' : 'Resumo do pagamento',
    `${pessoa(s.contrato.profissional, 'Profissional contratado')}${kv([
      ['Valor acordado', dinheiro(p.valor)],
      ['Total', dinheiro(p.valor)],
      ['Situação', nome(p.estado)],
      ['Data do pagamento', p.pagoEm ? data(p.pagoEm) : null],
      ['Referência', p.estado === 'PAGO' ? t?.referencia : null],
    ])}<p class="help">Pagamento simulado para a demonstração acadêmica.</p>${acao}`,
  )}${historico ? cartao('Tentativas', historico) : ''}</div></div>`;
};
acoes.cobrar = async (b) => {
  await api(base(b.dataset.id) + '/pagamento/tentativas', 'POST', {});
  ir('checkout/' + b.dataset.id);
};
rotas.checkout = async ([id]) => {
  const s = await api(base(id)),
    p = await api(base(id) + '/pagamento');
  const t = p.tentativas.find((x) => x.estado === 'EM_PROCESSAMENTO');
  if (s.clienteId !== estado.usuario.id)
    throw new Error('Apenas o cliente pode responder à cobrança.');
  return `<div class="narrow">${cab('Provedor de demonstração', s.titulo, 'pagamento/' + id)}
  ${cartao(
    'Simular resultado da cobrança',
    `<p class="price">${dinheiro(p.valor)}</p><p class="muted">Escolha a resposta do provedor fictício. Nenhum dinheiro será movimentado.</p>
  ${
    t
      ? `<div class="actions">${[
          ['Aprovar pagamento', 'PAGO', 'verde'],
          ['Recusar pagamento', 'RECUSADO', 'secundario'],
          ['Expirar cobrança', 'EXPIRADO', 'secundario'],
        ]
          .map(([n, r, c]) =>
            botao(n, 'resultado', id, c, `data-ref="${h(t.referencia)}" data-resultado="${r}"`),
          )
          .join('')}</div>`
      : link('Consultar pagamento', 'pagamento/' + id)
  }`,
  )}</div>`;
};
acoes.resultado = async (b) => {
  await api('/simulador' + base(b.dataset.id) + '/cobrancas/' + b.dataset.ref, 'POST', {
    resultado: b.dataset.resultado,
  });
  aviso('Resultado do provedor registrado.');
  ir('pagamento/' + b.dataset.id);
};

rotas.conversas = async () => {
  const lista = await api('/conversas');
  return `${cab('Conversas')}<div class="grid2 fluid">${lista.length ? lista.map((c) => `<a class="card" href="#conversa/${c.id}">${pessoa(estado.usuario.id === c.clienteId ? c.profissional : c.cliente, c.titulo)}<small class="help">${c.permiteEnviar ? 'Abrir conversa' : 'Somente leitura'}</small></a>`).join('') : vazio('Nenhuma conversa disponível.', 'O chat é criado quando um profissional envia uma proposta.')}</div>`;
};
const bolhas = (msgs) =>
  msgs.length
    ? msgs
        .map(
          (m) =>
            `<div class="bubble ${m.autorId === estado.usuario.id ? 'mine' : ''}"><small>${h(m.autor)}</small><p class="pre">${h(m.texto)}</p><small>${data(m.enviadaEm)}</small></div>`,
        )
        .join('')
    : vazio('A conversa ainda está vazia.');
rotas.conversa = async ([id]) => {
  const c = await api('/conversas/' + Number(id));
  return `<div class="narrow">${cab(estado.usuario.id === c.clienteId ? c.profissional : c.cliente, c.titulo, 'conversas')}<section class="form-card">
   <div class="chatbox" data-chat="${c.id}" data-ultimo="${c.mensagens.at(-1)?.id || 0}" data-aberto="${c.permiteEnviar}">${bolhas(c.mensagens)}</div>
   ${c.permiteEnviar ? `<form class="fields" data-form="mensagem" data-id="${c.id}">${campo('Mensagem', area('texto', '', 'required maxlength="2000"'))}${msgErro}${submit('Enviar mensagem', 'verde')}</form>` : '<p class="notice">Conversa disponível apenas para leitura.</p>'}${link('Ver serviço', rota(c.servicoId), 'link')}</section></div>`;
};
formularios.mensagem = async (f) => {
  await api('/conversas/' + f.dataset.id + '/mensagens', 'POST', { texto: dados(f).texto });
  f.reset();
  await atualizarChat();
};
let chatOcupado = false;
async function atualizarChat() {
  const el = $('[data-chat]');
  if (!el || chatOcupado || document.hidden) return;
  chatOcupado = true;
  try {
    const c = await api('/conversas/' + el.dataset.chat);
    if (!el.isConnected) return;
    if (String(c.permiteEnviar) !== el.dataset.aberto) {
      atualizar();
      return;
    }
    const ultimo = String(c.mensagens.at(-1)?.id || 0);
    if (el.dataset.ultimo !== ultimo) {
      el.innerHTML = bolhas(c.mensagens);
      el.dataset.ultimo = ultimo;
      el.scrollTop = el.scrollHeight;
    }
  } catch (e) {
    if (e.status === 401) atualizar();
  } finally {
    chatOcupado = false;
  }
}
setInterval(atualizarChat, 4000);
setInterval(() => {
  const el = $('[data-cronometro]');
  if (!el) return;
  const seg = Math.max(0, Math.floor((Date.now() - Date.parse(el.dataset.cronometro)) / 1000));
  el.textContent = [Math.floor(seg / 3600), Math.floor(seg / 60) % 60, seg % 60]
    .map((v) => String(v).padStart(2, '0'))
    .join(':');
}, 500);

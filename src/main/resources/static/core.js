export const estado = {
  usuario: null,
  categorias: [],
  destino: null,
  rascunho: null,
  retorno: null,
};
export const rotas = {},
  acoes = {},
  formularios = {},
  mudancas = {};
export const $ = (s) => document.querySelector(s);
export const h = (v) =>
  String(v ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
export const ic = (n) => `<i aria-hidden="true" class="ico i-${n}"></i>`;
export const dinheiro = (v) =>
  v == null ? '—' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
export const data = (v) =>
  v
    ? new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'America/Sao_Paulo',
      }).format(new Date(v))
    : 'A combinar';
export function localInput(v) {
  if (!v) return '';
  const partes = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(v));
  return partes.replace(' ', 'T');
}
export function instante(valor) {
  if (!valor) return null;
  const alvo = Date.parse(valor + 'Z');
  let utc = alvo;
  for (let i = 0; i < 2; i++) utc += alvo - Date.parse(localInput(utc) + 'Z');
  return new Date(utc).toISOString();
}
export const nomes = {
  ABERTA: 'Aberta',
  CONTRATADO: 'Contratado',
  AGENDADO: 'Agendado',
  EM_ANDAMENTO: 'Em andamento',
  AGUARDANDO_CONFIRMACAO: 'Aguardando confirmação',
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado',
  ACEITA: 'Aceita',
  PENDENTE: 'Pendente',
  RETIRADA: 'Retirada',
  ENCERRADA: 'Encerrada',
  NAO_SELECIONADA: 'Não selecionada',
  CONFIRMADO: 'Confirmado',
  RECUSADO: 'Recusado',
  EXPIRADO: 'Expirado',
  EM_PROCESSAMENTO: 'Em processamento',
  PAGO: 'Pago',
  SUBSTITUIDO: 'Substituído',
  INVALIDADO: 'Invalidado',
};
export const nome = (s) => nomes[s] || s || '';
export const etiqueta = (s) =>
  `<span class="pill ${['CONCLUIDO', 'PAGO', 'CONFIRMADO', 'ACEITA'].includes(s) ? 'green' : ['CANCELADO', 'RECUSADO', 'EXPIRADO', 'INVALIDADO'].includes(s) ? 'red' : ['AGUARDANDO_CONFIRMACAO', 'PENDENTE', 'EM_PROCESSAMENTO'].includes(s) ? 'gold' : 'blue'}">${h(nome(s))}</span>`;
export const iconsCat = ['grid', 'lightning-charge', 'stars', 'wrench', 'brush', 'hammer', 'grid'];
export const link = (texto, rota, estilo = 'secundario', icone = '') =>
  `<a href="#${h(rota)}" class="btn ${estilo}">${icone ? ic(icone) : ''}${h(texto)}</a>`;
export const botao = (texto, acao, id = '', estilo = 'secundario', extra = '') =>
  `<button type="button" class="btn ${estilo}" data-acao="${acao}" data-id="${h(id)}" ${extra}>${h(texto)}</button>`;
let numeroCampo = 0;
export function campo(rotulo, html) {
  const id = 'campo-' + ++numeroCampo;
  const controle = html.replace(/^<(input|textarea|select)\b/, '<$1 id="' + id + '"');
  return `<div class="field"><label for="${id}">${h(rotulo)}</label>${controle}</div>`;
}
export const inp = (nome, tipo = 'text', valor = '', attrs = '') =>
  `<input name="${nome}" type="${tipo}" value="${h(valor)}" ${attrs}>`;
export const area = (nome, valor = '', attrs = '') =>
  `<textarea name="${nome}" ${attrs}>${h(valor)}</textarea>`;
export const vazio = (titulo, desc = '') =>
  `<div class="empty"><strong>${h(titulo)}</strong>${h(desc)}</div>`;
export const msgErro = '<div class="error" data-erro role="alert"></div>';
export const submit = (texto, cor = 'primario') =>
  `<button class="btn ${cor}" type="submit">${h(texto)}</button>`;
export const cab = (titulo, sub = '', voltar = 'inicio') =>
  `<div class="page-head">${voltar ? `<a class="back" href="#${h(voltar)}">${ic('arrow-left')} Voltar</a>` : ''}<h1>${h(titulo)}</h1>${sub ? `<p class="lede">${h(sub)}</p>` : ''}</div>`;
export const kv = (items) =>
  `<dl class="facts">${items
    .filter((x) => x[1] != null && x[1] !== '')
    .map(([a, b]) => `<div><dt>${h(a)}</dt><dd>${h(b)}</dd></div>`)
    .join('')}</dl>`;
export const reputacao = (r) =>
  r?.totalAvaliacoes
    ? `${Number(r.media).toLocaleString('pt-BR', { minimumFractionDigits: 1 })} ★ · ${r.totalAvaliacoes} avaliação(ões)`
    : 'Sem avaliações';
export const dados = (form) => Object.fromEntries(new FormData(form));
export const op = () => crypto.randomUUID();
export async function api(caminho, metodo = 'GET', body, headers = {}) {
  const resposta = await fetch('/api' + caminho, {
    method: metodo,
    credentials: 'same-origin',
    headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const tipo = resposta.headers.get('Content-Type') || '';
  const valor = tipo.includes('application/json') ? await resposta.json() : null;
  if (!resposta.ok) {
    const e = new Error(valor?.mensagem || 'Não foi possível concluir a operação.');
    e.status = resposta.status;
    throw e;
  }
  return valor;
}
export const ir = (rota) => {
  if (location.hash === '#' + rota) atualizar();
  else location.hash = rota;
};
export function atualizar() {
  window.dispatchEvent(new Event('hub:atualizar'));
}
let timer;
export function aviso(m) {
  const el = $('#aviso');
  el.textContent = m;
  el.classList.add('visivel');
  clearTimeout(timer);
  timer = setTimeout(() => el.classList.remove('visivel'), 4400);
}
export function confirmar(titulo, texto, acao = 'Confirmar') {
  const d = $('#dialogo');
  $('#titulo-dialogo').textContent = titulo;
  $('#texto-dialogo').textContent = texto;
  $('#sim-dialogo').textContent = acao;
  d.returnValue = '';
  d.showModal();
  return new Promise((resolve) =>
    d.addEventListener('close', () => resolve(d.returnValue === 'sim'), { once: true }),
  );
}
export async function lerFotos(input) {
  const files = Array.from(input?.files || []);
  if (files.length > 3) throw new Error('Escolha até três fotos por etapa.');
  return Promise.all(
    files.map(async (file) => {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type))
        throw new Error('Selecione uma foto JPG, PNG ou WebP.');
      const url = URL.createObjectURL(file);
      try {
        const img = new Image();
        img.src = url;
        await img.decode();
        const f = Math.min(1, 1000 / Math.max(img.naturalWidth, img.naturalHeight)),
          c = document.createElement('canvas');
        c.width = Math.max(1, Math.round(img.naturalWidth * f));
        c.height = Math.max(1, Math.round(img.naturalHeight * f));
        const ctx = c.getContext('2d');
        ctx.fillStyle = '#fff';
        ctx.fillRect(0, 0, c.width, c.height);
        ctx.drawImage(img, 0, 0, c.width, c.height);
        return c.toDataURL('image/jpeg', 0.76);
      } finally {
        URL.revokeObjectURL(url);
      }
    }),
  );
}

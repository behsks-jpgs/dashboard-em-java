/* RF06 — Cliente da API REST do Thindesk.
 * Centraliza URL base, timeout, conversão de filtros em query string e tratamento de erros. */
const Api = (() => {
  // Mesmo servidor por padrão; para outra origem defina window.THINDESK_API antes deste script.
  const BASE = (window.THINDESK_API || '') + '/api/v1';
  const TIMEOUT_MS = 10000;

  class ErroApi extends Error {
    constructor(status, codigo, mensagem, detalhes = []) {
      super(mensagem);
      this.status = status;
      this.codigo = codigo;
      this.detalhes = detalhes;
    }
  }

  function query(params = {}) {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0)) return;
      q.set(k, Array.isArray(v) ? v.join(',') : v);
    });
    const s = q.toString();
    return s ? `?${s}` : '';
  }

  async function requisitar(metodo, caminho, { params, corpo } = {}) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
    let resp;
    try {
      resp = await fetch(BASE + caminho + query(params), {
        method: metodo,
        headers: corpo ? { 'Content-Type': 'application/json' } : {},
        body: corpo ? JSON.stringify(corpo) : undefined,
        signal: ctrl.signal,
      });
    } catch (e) {
      throw new ErroApi(0, 'SEM_CONEXAO',
        e.name === 'AbortError' ? 'A API demorou demais para responder.' : 'Não foi possível conectar à API.');
    } finally {
      clearTimeout(timer);
    }
    const texto = await resp.text();
    let dados = null;
    try { dados = texto ? JSON.parse(texto) : null; } catch { /* resposta não-JSON */ }
    if (!resp.ok) {
      const e = dados && dados.erro;
      throw new ErroApi(resp.status, e ? e.codigo : 'HTTP_' + resp.status,
        e ? e.mensagem : `Erro ${resp.status} ao chamar a API.`, e ? e.detalhes : []);
    }
    return dados;
  }

  return {
    ErroApi,
    filtros: () => requisitar('GET', '/filtros'),
    health: () => requisitar('GET', '/health'),
    indicadores: (f) => requisitar('GET', '/dashboard/indicadores', { params: f }),
    serie: (f, agrupamento) => requisitar('GET', '/dashboard/chamados-por-periodo', { params: { ...f, agrupamento } }),
    situacao: (f) => requisitar('GET', '/dashboard/situacao', { params: f }),
    tipos: (f, limite = 8) => requisitar('GET', '/dashboard/tipos-mais-atendidos', { params: { ...f, limite } }),
    tecnicos: (f) => requisitar('GET', '/dashboard/desempenho-tecnicos', { params: f }),
    chamados: (params) => requisitar('GET', '/chamados', { params }),
    chamado: (id) => requisitar('GET', `/chamados/${encodeURIComponent(id)}`),
    criarChamado: (corpo) => requisitar('POST', '/chamados', { corpo }),
    alterarStatus: (id, corpo) => requisitar('PATCH', `/chamados/${encodeURIComponent(id)}/status`, { corpo }),
    adicionarConsideracao: (id, corpo) => requisitar('POST', `/chamados/${encodeURIComponent(id)}/consideracoes`, { corpo }),
  };
})();

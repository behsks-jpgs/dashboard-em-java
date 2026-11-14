/* Dashboard administrativo do Thindesk — integra os endpoints da API (RF01–RF07). */
(() => {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const fmt = Graficos.fmt;
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const INTERVALO_MS = 30000;

  const estado = {
    agrupamento: 'dia',
    pagina: 1,
    porPagina: 10,
    busca: '',
    soAtrasados: false,
    opcoes: null,
    timer: null,
    carregando: false,
    detalheId: null,
  };

  // ------------------------------------------------------------------ utilidades
  const hojeISO = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const menosDias = (iso, n) => {
    const d = new Date(iso + 'T12:00:00');
    d.setDate(d.getDate() - n);
    return d.toISOString().slice(0, 10);
  };
  const dataBR = (iso) => (iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—');
  const dia = (iso) => (iso ? iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4) : '');

  function duracaoMin(min) {
    if (min == null) return '—';
    if (min < 60) return `${Math.round(min)}<small>min</small>`;
    const h = Math.floor(min / 60), m = Math.round(min % 60);
    return `${h}<small>h</small> ${String(m).padStart(2, '0')}<small>min</small>`;
  }
  function duracaoHoras(h) {
    if (h == null) return '—';
    if (h < 48) return `${h.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}<small>h</small>`;
    return `${(h / 24).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}<small>dias</small>`;
  }
  const pct = (v) => (v == null ? '—' : `${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}<small>%</small>`);

  function toast(msg, erro = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast' + (erro ? ' erro' : '');
    t.hidden = false;
    clearTimeout(toast.t);
    toast.t = setTimeout(() => { t.hidden = true; }, 3500);
  }

  function mensagemErro(e) {
    if (e.detalhes && e.detalhes.length) return e.detalhes.map((d) => `${d.campo}: ${d.problema}`).join(' · ');
    return e.message;
  }

  // ------------------------------------------------------------------ filtros (RF05)
  function filtrosAtuais() {
    const periodo = $('#f-periodo').value;
    let dataInicio, dataFim;
    if (periodo === 'custom') {
      dataInicio = $('#f-inicio').value;
      dataFim = $('#f-fim').value;
    } else {
      dataFim = hojeISO();
      dataInicio = menosDias(dataFim, Number(periodo) - 1);
    }
    return {
      dataInicio,
      dataFim,
      status: $$('#f-status input:checked').map((i) => i.value),
      prioridade: $('#f-prioridade').value,
      categoriaId: $('#f-categoria').value,
      tecnicoId: $('#f-tecnico').value,
    };
  }

  function preencherSelect(sel, opcoes, primeiro) {
    sel.innerHTML = (primeiro ? `<option value="">${primeiro}</option>` : '') +
      opcoes.map((o) => `<option value="${esc(o.valor)}">${esc(o.rotulo)}</option>`).join('');
  }

  async function carregarOpcoes() {
    const o = await Api.filtros();
    estado.opcoes = o;
    preencherSelect($('#f-prioridade'), o.prioridades, 'Todas');
    preencherSelect($('#f-categoria'), o.categorias, 'Todos');
    preencherSelect($('#f-tecnico'), o.tecnicos, 'Todos');
    preencherSelect($('#novo-categoria'), o.categorias);
    preencherSelect($('#novo-prioridade'), o.prioridades);
    $('#novo-prioridade').value = 'media';
    $('#f-status .opcoes').innerHTML = o.status.map((s) =>
      `<label><input type="checkbox" value="${s.valor}"> ${esc(s.rotulo)}</label>`).join('');
    $$('#f-status input').forEach((i) => i.addEventListener('change', () => { resumoStatus(); aoMudarFiltro(); }));
  }

  function resumoStatus() {
    const marc = $$('#f-status input:checked');
    $('#f-status summary').textContent = !marc.length ? 'Todos'
      : marc.length === 1 ? marc[0].parentElement.textContent.trim() : `${marc.length} selecionados`;
  }

  function aoMudarFiltro() {
    estado.pagina = 1;
    carregarTudo();
  }

  // ------------------------------------------------------------------ carregamento (RF06/RF07)
  async function carregarTudo() {
    const f = filtrosAtuais();
    if ($('#f-periodo').value === 'custom' && f.dataInicio && f.dataFim && f.dataInicio > f.dataFim) {
      return toast('A data inicial não pode ser maior que a final.', true);
    }
    estado.carregando = true;
    $$('.grafico, .kpis').forEach((e) => e.classList.add('carregando'));
    const tarefas = [
      Api.indicadores(f).then(renderKpis),
      Api.serie(f, estado.agrupamento).then(renderSerie),
      Api.situacao(f).then(renderSituacao),
      Api.tipos(f).then(renderTipos),
      Api.tecnicos(f).then(renderTecnicos),
      carregarChamados(),
    ];
    const resultados = await Promise.allSettled(tarefas);
    $$('.carregando').forEach((e) => e.classList.remove('carregando'));
    estado.carregando = false;

    const falhas = resultados.filter((r) => r.status === 'rejected').map((r) => r.reason);
    const conexao = $('#status-conexao');
    if (falhas.length) {
      const e = falhas[0];
      $('#erro-texto').textContent = `Não foi possível atualizar ${falhas.length === tarefas.length ? 'o painel' : 'parte do painel'}: ${mensagemErro(e)}`;
      $('#erro-global').hidden = false;
      conexao.className = 'conexao erro';
      conexao.textContent = e.status === 0 ? 'API indisponível' : 'Falha na atualização';
      console.error(falhas);
    } else {
      $('#erro-global').hidden = true;
      conexao.className = 'conexao ok';
      conexao.textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
    }
  }

  // ------------------------------------------------------------------ RF01 indicadores
  function renderKpis(r) {
    const i = r.indicadores;
    const c = r.comparativo;
    const delta = (chave, melhorSubir, pontos = false) => {
      if (!c) return '';
      const atual = i[chave], ant = c.valoresAnteriores[chave];
      if (atual == null || ant == null) return '<div class="delta">sem base de comparação</div>';
      const diff = pontos ? atual - ant : c.variacaoPercentual[chave];
      if (diff == null) return '<div class="delta">sem base de comparação</div>';
      if (Math.abs(diff) < 0.05) return '<div class="delta">= estável</div>';
      const bom = (diff > 0) === melhorSubir;
      const txt = Math.abs(diff).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + (pontos ? ' p.p.' : '%');
      return `<div class="delta ${bom ? 'bom' : 'ruim'}">${diff > 0 ? '▲' : '▼'} ${txt}</div>`;
    };
    const cards = [
      ['Total de chamados', fmt(i.total), delta('total', true), ''],
      ['Pendentes', fmt(i.pendentes), delta('pendentes', false), ''],
      ['Atrasados (SLA vencido)', fmt(i.atrasados), `<div class="delta">${fmt(i.abertos)} sem atendimento</div>`, i.atrasados > 0 ? 'destaque-ruim' : ''],
      ['Resolvidos', fmt(i.resolvidos), delta('resolvidos', true), ''],
      ['Taxa de resolução', pct(i.taxaResolucao), delta('taxaResolucao', true, true), ''],
      ['SLA cumprido', pct(i.slaCumpridoPercentual), delta('slaCumpridoPercentual', true, true), ''],
      ['Tempo médio 1º atendimento', duracaoMin(i.tempoMedioPrimeiroAtendimentoMin), delta('tempoMedioPrimeiroAtendimentoMin', false), ''],
      ['Tempo médio de resolução', duracaoHoras(i.tempoMedioResolucaoHoras), delta('tempoMedioResolucaoHoras', false), ''],
      ['Satisfação média', i.avaliacaoMedia == null ? '—' : `${i.avaliacaoMedia.toLocaleString('pt-BR')}<small>/5</small>`, '<div class="delta">avaliação do usuário</div>', ''],
    ];
    $('#kpis').innerHTML = cards.map(([rot, val, d, cls]) =>
      `<div class="kpi ${cls}"><div class="rotulo">${rot}</div><div class="valor">${val}</div>${d}</div>`).join('');
    $('#nota-comparativo').textContent = c
      ? `Período: ${dia(r.filtros.dataInicio)} a ${dia(r.filtros.dataFim)} · variações comparadas a ${dia(c.periodoAnterior.dataInicio)} – ${dia(c.periodoAnterior.dataFim)}.`
      : '';
  }

  // ------------------------------------------------------------------ RF02 série temporal
  function renderSerie(r) {
    const pontos = r.serie.map((p) => ({
      ...p,
      titulo: r.agrupamento === 'semana' ? `Semana de ${p.rotulo}` : r.agrupamento === 'dia' ? dia(p.periodo) : p.rotulo,
    }));
    Graficos.linhas($('#grafico-serie'), pontos, [
      { chave: 'abertos', nome: 'Abertos', cor: 'var(--serie-1)' },
      { chave: 'resolvidos', nome: 'Resolvidos', cor: 'var(--serie-2)' },
    ]);
  }

  // ------------------------------------------------------------------ RF03 situação
  function renderSituacao(r) {
    Graficos.barras($('#grafico-status'), r.porStatus.map((s) => ({
      rotulo: s.rotulo,
      valor: s.quantidade,
      texto: `${fmt(s.quantidade)} · ${s.percentual.toLocaleString('pt-BR')}%`,
      tooltip: `<b>${s.rotulo}</b>${fmt(s.quantidade)} chamados (${s.percentual.toLocaleString('pt-BR')}%)`,
    })));
    $('#alerta-atrasados').innerHTML = r.atrasados > 0
      ? `<span class="icone">!</span><span><strong>${fmt(r.atrasados)}</strong> chamado(s) pendente(s) com SLA vencido. <a href="#" id="ver-atrasados">Ver lista</a></span>`
      : '<span>Nenhum chamado pendente fora do SLA. ✓</span>';
    const link = $('#ver-atrasados');
    if (link) link.addEventListener('click', (e) => {
      e.preventDefault();
      $('#so-atrasados').checked = true;
      estado.soAtrasados = true;
      estado.pagina = 1;
      carregarChamados();
      $('#tabela-chamados').scrollIntoView({ behavior: 'smooth' });
    });

    Graficos.barras($('#grafico-prioridade'), r.pendentesPorPrioridade.slice().reverse().map((p) => ({
      rotulo: p.rotulo,
      valor: p.quantidade,
      texto: `${fmt(p.quantidade)}${p.atrasados ? ` · ${fmt(p.atrasados)} atras.` : ''}`,
      tooltip: `<b>Prioridade ${p.rotulo}</b>${fmt(p.quantidade)} pendentes<br>${fmt(p.atrasados)} com SLA vencido`,
    })), { cor: 'var(--serie-1)' });

    Graficos.barras($('#grafico-idade'), r.pendentesPorIdade.map((p) => ({
      rotulo: p.rotulo, valor: p.quantidade,
    })), { cor: 'var(--serie-1)' });
  }

  // ------------------------------------------------------------------ RF04 tipos mais atendidos
  function renderTipos(r) {
    Graficos.barras($('#grafico-tipos'), r.ranking.map((t) => ({
      rotulo: `${t.posicao}º ${t.categoria}`,
      valor: t.atendidos,
      texto: `${fmt(t.atendidos)} · ${t.percentual.toLocaleString('pt-BR')}%`,
      tooltip: `<b>${esc(t.categoria)}</b>${fmt(t.atendidos)} atendidos de ${fmt(t.total)}<br>` +
        `Resolvidos: ${fmt(t.resolvidos)}<br>Tempo médio: ${t.tempoMedioResolucaoHoras == null ? '—' : t.tempoMedioResolucaoHoras.toLocaleString('pt-BR') + ' h'}<br>` +
        `SLA cumprido: ${t.slaCumpridoPercentual.toLocaleString('pt-BR')}%`,
    })));
  }

  function renderTecnicos(r) {
    const t = $('#tabela-tecnicos');
    if (!r.tecnicos.length) { t.innerHTML = '<tr><td class="vazio">Sem atendimentos no período.</td></tr>'; return; }
    t.innerHTML = `<thead><tr><th>Técnico</th><th>Nível</th><th class="num">Atendidos</th><th class="num">Resolvidos</th>
      <th class="num">Pendentes</th><th class="num">Tempo médio de resolução</th><th class="num">SLA cumprido</th><th class="num">Satisfação</th></tr></thead><tbody>` +
      r.tecnicos.map((x) => `<tr><td>${esc(x.nome)}</td><td>${esc(x.nivel ?? '—')}</td><td class="num">${fmt(x.atendidos)}</td>
        <td class="num">${fmt(x.resolvidos)}</td><td class="num">${fmt(x.pendentes)}</td>
        <td class="num">${x.tempoMedioResolucaoHoras == null ? '—' : x.tempoMedioResolucaoHoras.toLocaleString('pt-BR') + ' h'}</td>
        <td class="num">${x.slaCumpridoPercentual.toLocaleString('pt-BR')}%</td>
        <td class="num">${x.avaliacaoMedia == null ? '—' : x.avaliacaoMedia.toLocaleString('pt-BR') + ' / 5'}</td></tr>`).join('') + '</tbody>';
  }

  // ------------------------------------------------------------------ lista de chamados
  async function carregarChamados() {
    const r = await Api.chamados({
      ...filtrosAtuais(),
      pagina: estado.pagina,
      porPagina: estado.porPagina,
      busca: estado.busca,
      atrasados: estado.soAtrasados ? 'true' : '',
    });
    const t = $('#tabela-chamados');
    if (!r.dados.length) {
      t.innerHTML = '<tr><td class="vazio">Nenhum chamado encontrado.</td></tr>';
    } else {
      t.innerHTML = `<thead><tr><th>#</th><th>Título</th><th>Tipo</th><th>Prioridade</th><th>Status</th>
        <th>Técnico</th><th>Abertura</th><th>Prazo SLA</th></tr></thead><tbody>` +
        r.dados.map((c) => `<tr data-id="${esc(c.id)}"><td>${c.numero}</td><td class="titulo">${esc(c.titulo)}<br><small style="color:var(--tinta-3)">${esc(c.solicitante)} · ${esc(c.setor)}</small></td>
          <td>${esc(c.categoria)}</td><td><span class="prio ${c.prioridade}">${c.prioridadeRotulo}</span></td>
          <td><span class="badge ${c.status}">${c.statusRotulo}</span></td><td>${esc(c.tecnico ?? '—')}</td>
          <td>${dataBR(c.dataAbertura)}</td><td>${dataBR(c.prazoSla)} ${c.atrasado ? '<span class="flag-atraso">● atrasado</span>' : ''}</td></tr>`).join('') + '</tbody>';
      $$('#tabela-chamados tbody tr').forEach((tr) => tr.addEventListener('click', () => abrirDetalhe(tr.dataset.id)));
    }
    const p = r.paginacao;
    $('#paginacao').innerHTML = `<span>${fmt(p.total)} chamado(s) · página ${p.total ? p.pagina : 0} de ${p.totalPaginas}</span>
      <div class="botoes"><button class="btn" id="pg-ant" ${p.pagina <= 1 ? 'disabled' : ''}>← Anterior</button>
      <button class="btn" id="pg-prox" ${p.pagina >= p.totalPaginas ? 'disabled' : ''}>Próxima →</button></div>`;
    $('#pg-ant').onclick = () => { estado.pagina--; carregarChamados().catch(falhaLista); };
    $('#pg-prox').onclick = () => { estado.pagina++; carregarChamados().catch(falhaLista); };
  }
  const falhaLista = (e) => toast('Falha ao carregar chamados: ' + mensagemErro(e), true);

  // ------------------------------------------------------------------ detalhe + RF07 atualização
  async function abrirDetalhe(id) {
    estado.detalheId = id;
    const drawer = $('#drawer');
    drawer.classList.add('aberto');
    drawer.setAttribute('aria-hidden', 'false');
    $('#drawer-conteudo').innerHTML = '<p class="vazio">Carregando…</p>';
    try {
      renderDetalhe(await Api.chamado(id));
    } catch (e) {
      $('#drawer-conteudo').innerHTML = `<p class="erro-form">${esc(mensagemErro(e))}</p>`;
    }
  }

  function fecharDetalhe() {
    estado.detalheId = null;
    $('#drawer').classList.remove('aberto');
    $('#drawer').setAttribute('aria-hidden', 'true');
  }

  function renderDetalhe(d) {
    const c = d.resumo;
    const rotuloStatus = Object.fromEntries(estado.opcoes.status.map((s) => [s.valor, s.rotulo]));
    const tecnicos = estado.opcoes.tecnicos;
    const consid = d.consideracoes.length
      ? d.consideracoes.slice().reverse().map((x) => `<li class="${x.tipo}"><strong>${x.tipoRotulo}</strong> · ${esc(x.tecnico ?? 'Sistema')}
          <div>${esc(x.texto)}</div><div class="quando">${dataBR(x.criadoEm)}</div></li>`).join('')
      : '<li><span class="quando">Nenhuma consideração registrada.</span></li>';
    const hist = d.historico.slice().reverse().map((h) => `<li><strong>${h.de ? rotuloStatus[h.de] + ' → ' : ''}${rotuloStatus[h.para]}</strong>
        ${h.observacao ? `<div>${esc(h.observacao)}</div>` : ''}<div class="quando">${dataBR(h.alteradoEm)}</div></li>`).join('');

    $('#drawer-conteudo').innerHTML = `
      <span class="num-chamado">Chamado #${c.numero}</span>
      <h3 id="drawer-titulo">${esc(c.titulo)}</h3>
      <span class="badge ${c.status}">${c.statusRotulo}</span> <span class="prio ${c.prioridade}">Prioridade ${c.prioridadeRotulo}</span>
      ${c.atrasado ? '<span class="flag-atraso"> ● SLA vencido</span>' : ''}
      <p>${esc(d.descricao)}</p>
      <dl class="meta">
        <div><dt>Solicitante</dt><dd>${esc(c.solicitante)} (${esc(c.setor)})</dd></div>
        <div><dt>Tipo</dt><dd>${esc(c.categoria)}</dd></div>
        <div><dt>Técnico</dt><dd>${esc(c.tecnico ?? '— não atribuído')}</dd></div>
        <div><dt>Abertura</dt><dd>${dataBR(c.dataAbertura)}</dd></div>
        <div><dt>Prazo SLA</dt><dd>${dataBR(c.prazoSla)}</dd></div>
        <div><dt>1º atendimento</dt><dd>${d.minutosAtePrimeiroAtendimento == null ? '—' : fmt(d.minutosAtePrimeiroAtendimento) + ' min após abertura'}</dd></div>
        <div><dt>Resolução</dt><dd>${d.horasAteResolucao == null ? '—' : fmt(d.horasAteResolucao) + ' h após abertura'}</dd></div>
        <div><dt>Avaliação</dt><dd>${d.avaliacao ? '★'.repeat(d.avaliacao) + '☆'.repeat(5 - d.avaliacao) : '—'}</dd></div>
      </dl>

      ${d.transicoesPermitidas.length ? `
      <h4>Atualizar status</h4>
      <form class="form-acao" id="form-status">
        <div class="linha">
          <select name="status">${d.transicoesPermitidas.map((s) => `<option value="${s}">${rotuloStatus[s]}</option>`).join('')}</select>
          <select name="tecnicoId"><option value="">${c.tecnico ? 'Manter ' + esc(c.tecnico) : 'Selecione o técnico'}</option>
            ${tecnicos.map((t) => `<option value="${esc(t.valor)}">${esc(t.rotulo)}</option>`).join('')}</select>
        </div>
        <textarea name="observacao" rows="2" placeholder="Observação (obrigatória para resolver ou cancelar)"></textarea>
        <p class="erro-form" id="erro-status"></p>
        <button class="btn primario" type="submit">Salvar status</button>
      </form>` : ''}

      <h4>Considerações técnicas</h4>
      ${c.status !== 'cancelado' ? `
      <form class="form-acao" id="form-consid" style="margin-bottom:14px">
        <div class="linha">
          <select name="tipo"><option value="diagnostico">Diagnóstico</option><option value="observacao">Observação</option><option value="solucao">Solução</option></select>
        </div>
        <textarea name="texto" rows="2" placeholder="Descreva o diagnóstico, a observação ou a solução"></textarea>
        <p class="erro-form" id="erro-consid"></p>
        <button class="btn" type="submit">Adicionar consideração</button>
      </form>` : ''}
      <ul class="timeline">${consid}</ul>

      <h4>Histórico de status</h4>
      <ul class="timeline">${hist}</ul>`;

    const fs = $('#form-status');
    if (fs) fs.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const dados = Object.fromEntries(new FormData(fs));
      try {
        const novo = await Api.alterarStatus(c.id, dados);
        toast(`Chamado #${c.numero} atualizado para "${novo.resumo.statusRotulo}".`);
        renderDetalhe(novo);
        carregarTudo();
      } catch (e) { $('#erro-status').textContent = mensagemErro(e); }
    });
    const fc = $('#form-consid');
    if (fc) fc.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      try {
        renderDetalhe(await Api.adicionarConsideracao(c.id, Object.fromEntries(new FormData(fc))));
        toast('Consideração técnica registrada.');
      } catch (e) { $('#erro-consid').textContent = mensagemErro(e); }
    });
  }

  // ------------------------------------------------------------------ novo chamado
  function configurarNovo() {
    const modal = $('#modal-novo');
    $('#btn-novo').addEventListener('click', () => { $('#erro-novo').textContent = ''; modal.showModal(); });
    $('#cancelar-novo').addEventListener('click', () => modal.close());
    $('#form-novo').addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const form = ev.target;
      try {
        const c = await Api.criarChamado(Object.fromEntries(new FormData(form)));
        modal.close();
        form.reset();
        $('#novo-prioridade').value = 'media';
        toast(`Chamado #${c.resumo.numero} aberto.`);
        carregarTudo();
      } catch (e) { $('#erro-novo').textContent = mensagemErro(e); }
    });
  }

  // ------------------------------------------------------------------ atualização automática (RF07)
  function agendar() {
    clearInterval(estado.timer);
    if ($('#auto-refresh').checked) {
      estado.timer = setInterval(() => { if (!document.hidden && !estado.carregando) carregarTudo(); }, INTERVALO_MS);
    }
  }

  // ------------------------------------------------------------------ início
  function ligarEventos() {
    $('#f-periodo').addEventListener('change', () => {
      const v = $('#f-periodo').value;
      $$('.personalizado').forEach((e) => { e.hidden = v !== 'custom'; });
      if (v === 'custom') {
        if (!$('#f-fim').value) $('#f-fim').value = hojeISO();
        if (!$('#f-inicio').value) $('#f-inicio').value = menosDias(hojeISO(), 29);
      } else {
        definirAgrupamento(v === '365' ? 'mes' : v === '90' ? 'semana' : 'dia', false);
      }
      aoMudarFiltro();
    });
    ['#f-inicio', '#f-fim', '#f-prioridade', '#f-categoria', '#f-tecnico'].forEach((s) => $(s).addEventListener('change', aoMudarFiltro));
    $('#btn-limpar').addEventListener('click', () => {
      $('#f-periodo').value = '30';
      $$('.personalizado').forEach((e) => { e.hidden = true; });
      $$('#f-status input').forEach((i) => { i.checked = false; });
      resumoStatus();
      ['#f-prioridade', '#f-categoria', '#f-tecnico'].forEach((s) => { $(s).value = ''; });
      $('#busca').value = ''; estado.busca = '';
      $('#so-atrasados').checked = false; estado.soAtrasados = false;
      definirAgrupamento('dia', false);
      aoMudarFiltro();
    });
    $$('.segmentado button').forEach((b) => b.addEventListener('click', () => definirAgrupamento(b.dataset.agrup, true)));
    let debounce;
    $('#busca').addEventListener('input', (e) => {
      clearTimeout(debounce);
      debounce = setTimeout(() => { estado.busca = e.target.value.trim(); estado.pagina = 1; carregarChamados().catch(falhaLista); }, 350);
    });
    $('#so-atrasados').addEventListener('change', (e) => { estado.soAtrasados = e.target.checked; estado.pagina = 1; carregarChamados().catch(falhaLista); });
    $('#btn-atualizar').addEventListener('click', carregarTudo);
    $('#btn-tentar').addEventListener('click', iniciar);
    $('#auto-refresh').addEventListener('change', agendar);
    $$('[data-fechar]').forEach((e) => e.addEventListener('click', fecharDetalhe));
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') fecharDetalhe(); });
    document.addEventListener('click', (e) => { if (!e.target.closest('#f-status')) $('#f-status').open = false; });
    let resize;
    window.addEventListener('resize', () => { clearTimeout(resize); resize = setTimeout(carregarTudo, 250); });
    configurarNovo();
  }

  function definirAgrupamento(a, recarregar) {
    estado.agrupamento = a;
    $$('.segmentado button').forEach((b) => b.classList.toggle('ativo', b.dataset.agrup === a));
    if (recarregar) Api.serie(filtrosAtuais(), a).then(renderSerie).catch((e) => toast(mensagemErro(e), true));
  }

  let eventosLigados = false;
  async function iniciar() {
    try {
      await carregarOpcoes();
      if (!eventosLigados) { ligarEventos(); eventosLigados = true; }
      await carregarTudo();
      agendar();
    } catch (e) {
      $('#erro-texto').textContent = 'Não foi possível conectar à API do Thindesk: ' + mensagemErro(e);
      $('#erro-global').hidden = false;
      $('#status-conexao').className = 'conexao erro';
      $('#status-conexao').textContent = 'API indisponível';
    }
  }

  iniciar();
})();

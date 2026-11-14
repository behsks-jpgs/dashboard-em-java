/* Gráficos em SVG puro (sem bibliotecas externas): linhas com crosshair e barras horizontais. */
const Graficos = (() => {
  const NS = 'http://www.w3.org/2000/svg';
  const tooltip = () => document.getElementById('tooltip');
  const fmt = (n) => Number(n).toLocaleString('pt-BR');

  function el(tag, attrs = {}, pai) {
    const e = document.createElementNS(NS, tag);
    Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
    if (pai) pai.appendChild(e);
    return e;
  }

  function mostrarTooltip(html, x, y) {
    const t = tooltip();
    t.innerHTML = html;
    t.hidden = false;
    const r = t.getBoundingClientRect();
    let left = x + 14, top = y + 14;
    if (left + r.width > window.innerWidth - 8) left = x - r.width - 14;
    if (top + r.height > window.innerHeight - 8) top = y - r.height - 14;
    t.style.left = left + 'px';
    t.style.top = top + 'px';
  }
  const esconderTooltip = () => { tooltip().hidden = true; };

  function escalaBonita(max) {
    if (max <= 0) return { max: 4, passo: 1 };
    const bruto = max / 4;
    const mag = Math.pow(10, Math.floor(Math.log10(bruto)));
    const passo = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((p) => p >= bruto);
    return { max: Math.ceil(max / passo) * passo, passo };
  }

  function vazio(container, texto = 'Sem dados para os filtros selecionados.') {
    container.innerHTML = `<div class="vazio">${texto}</div>`;
  }

  /** Gráfico de linhas: pontos = [{rotulo, ...}], series = [{chave, nome, cor}] */
  function linhas(container, pontos, series) {
    container.innerHTML = '';
    if (!pontos.length || pontos.every((p) => series.every((s) => !p[s.chave]))) return vazio(container);

    const leg = document.createElement('div');
    leg.className = 'legenda';
    leg.innerHTML = series.map((s) => `<span><i style="background:${s.cor}"></i>${s.nome}</span>`).join('');
    container.appendChild(leg);

    const W = container.clientWidth || 800;
    const H = (container.clientHeight || 280) - 26;
    const m = { t: 10, r: 86, b: 26, l: 40 };
    const iw = W - m.l - m.r, ih = H - m.t - m.b;
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img',
      'aria-label': 'Gráfico de chamados abertos e resolvidos por período' }, container);

    const maxV = Math.max(...pontos.flatMap((p) => series.map((s) => p[s.chave])));
    const { max, passo } = escalaBonita(maxV);
    const x = (i) => m.l + (pontos.length === 1 ? iw / 2 : (i * iw) / (pontos.length - 1));
    const y = (v) => m.t + ih - (v / max) * ih;

    for (let v = 0; v <= max; v += passo) {
      el('line', { x1: m.l, x2: m.l + iw, y1: y(v), y2: y(v), stroke: v === 0 ? 'var(--eixo)' : 'var(--grade)', 'stroke-width': 1 }, svg);
      const t = el('text', { x: m.l - 8, y: y(v) + 4, 'text-anchor': 'end', 'font-size': 11, fill: 'var(--tinta-3)' }, svg);
      t.textContent = fmt(v);
    }
    const saltos = Math.max(1, Math.ceil(pontos.length / Math.max(2, Math.floor(iw / 70))));
    pontos.forEach((p, i) => {
      if (i % saltos !== 0) return;
      const t = el('text', { x: x(i), y: H - 6, 'text-anchor': 'middle', 'font-size': 11, fill: 'var(--tinta-3)' }, svg);
      t.textContent = p.rotulo;
    });

    series.forEach((s) => {
      const d = pontos.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p[s.chave]).toFixed(1)}`).join('');
      el('path', { d, fill: 'none', stroke: s.cor, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }, svg);
    });
    // rótulos diretos no fim de cada linha (sem sobreposição)
    const ult = pontos[pontos.length - 1];
    const fins = series.map((s) => ({ s, yv: y(ult[s.chave]) })).sort((a, b) => a.yv - b.yv);
    for (let i = 1; i < fins.length; i++) if (fins[i].yv - fins[i - 1].yv < 14) fins[i].yv = fins[i - 1].yv + 14;
    fins.forEach(({ s, yv }) => {
      const t = el('text', { x: m.l + iw + 8, y: yv + 4, 'font-size': 12, fill: 'var(--tinta-2)' }, svg);
      t.textContent = `${s.nome} ${fmt(ult[s.chave])}`;
    });

    // camada de hover: crosshair + marcadores + tooltip
    const cruz = el('line', { y1: m.t, y2: m.t + ih, stroke: 'var(--eixo)', 'stroke-width': 1, visibility: 'hidden' }, svg);
    const marcadores = series.map((s) => el('circle', { r: 4.5, fill: s.cor, stroke: 'var(--superficie)', 'stroke-width': 2, visibility: 'hidden' }, svg));
    const area = el('rect', { x: m.l, y: m.t, width: iw, height: ih, fill: 'transparent' }, svg);
    area.addEventListener('mousemove', (ev) => {
      const r = svg.getBoundingClientRect();
      const px = ((ev.clientX - r.left) * W) / r.width;
      const i = Math.max(0, Math.min(pontos.length - 1, Math.round(((px - m.l) / iw) * (pontos.length - 1))));
      const p = pontos[i];
      cruz.setAttribute('x1', x(i)); cruz.setAttribute('x2', x(i)); cruz.setAttribute('visibility', 'visible');
      series.forEach((s, k) => {
        marcadores[k].setAttribute('cx', x(i)); marcadores[k].setAttribute('cy', y(p[s.chave]));
        marcadores[k].setAttribute('visibility', 'visible');
      });
      mostrarTooltip(`<b>${p.titulo || p.rotulo}</b>` + series.map((s) =>
        `<div class="linha-tt"><i style="background:${s.cor}"></i>${s.nome}: ${fmt(p[s.chave])}</div>`).join(''),
        ev.clientX, ev.clientY);
    });
    area.addEventListener('mouseleave', () => {
      cruz.setAttribute('visibility', 'hidden');
      marcadores.forEach((mk) => mk.setAttribute('visibility', 'hidden'));
      esconderTooltip();
    });
  }

  /** Barras horizontais: itens = [{rotulo, valor, texto?, tooltip?, cor?}] */
  function barras(container, itens, { cor = 'var(--serie-1)', sufixo = '' } = {}) {
    container.innerHTML = '';
    if (!itens.length || itens.every((i) => !i.valor)) return vazio(container);
    const W = container.clientWidth || 500;
    const linhaH = 30, gap = 2;
    const rotuloW = Math.min(150, W * 0.36), valorW = 84;
    const H = itens.length * (linhaH + gap);
    const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img' }, container);
    const max = Math.max(...itens.map((i) => i.valor)) || 1;
    const bw = W - rotuloW - valorW;

    itens.forEach((it, k) => {
      const y0 = k * (linhaH + gap);
      const g = el('g', {}, svg);
      const rot = el('text', { x: 0, y: y0 + linhaH / 2 + 4, 'font-size': 12.5, fill: 'var(--tinta)' }, g);
      rot.textContent = it.rotulo;
      el('rect', { x: rotuloW, y: y0 + 7, width: bw, height: linhaH - 14, rx: 4, fill: '#f0efec' }, g);
      const w = Math.max(it.valor > 0 ? 3 : 0, (it.valor / max) * bw);
      el('rect', { x: rotuloW, y: y0 + 7, width: w, height: linhaH - 14, rx: 4, fill: it.cor || cor }, g);
      const val = el('text', { x: rotuloW + bw + 8, y: y0 + linhaH / 2 + 4, 'font-size': 12.5, fill: 'var(--tinta-2)' }, g);
      val.textContent = it.texto ?? fmt(it.valor) + sufixo;
      const alvo = el('rect', { x: 0, y: y0, width: W, height: linhaH, fill: 'transparent' }, g);
      alvo.addEventListener('mousemove', (ev) => mostrarTooltip(it.tooltip || `<b>${it.rotulo}</b>${fmt(it.valor)}${sufixo}`, ev.clientX, ev.clientY));
      alvo.addEventListener('mouseleave', esconderTooltip);
    });
  }

  return { linhas, barras, vazio, fmt };
})();

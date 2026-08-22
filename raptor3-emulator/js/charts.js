// Przewijane wykresy telemetrii (strip chart) rysowane na canvasie 2D.

export class StripChart {
  constructor(canvas, { max, color, unit }) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.max = max;
    this.color = color;
    this.unit = unit;
    this.n = 240;                       // liczba próbek w oknie
    this.data = new Float32Array(this.n).fill(NaN);
    this.head = 0;
  }

  push(v) {
    this.data[this.head] = v;
    this.head = (this.head + 1) % this.n;
  }

  draw() {
    const { ctx, canvas } = this;
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    // siatka
    ctx.strokeStyle = 'rgba(120,160,220,0.14)';
    ctx.lineWidth = 1;
    for (let i = 1; i <= 3; i++) {
      const y = (H * i) / 4;
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    // przebieg
    ctx.strokeStyle = this.color;
    ctx.lineWidth = 2;
    ctx.shadowColor = this.color;
    ctx.shadowBlur = 6;
    ctx.beginPath();
    let started = false;
    for (let i = 0; i < this.n; i++) {
      const v = this.data[(this.head + i) % this.n];
      if (Number.isNaN(v)) { continue; }
      const x = (i / (this.n - 1)) * W;
      const y = H - Math.min(1, Math.max(0, v / this.max)) * (H - 3) - 1.5;
      if (!started) { ctx.moveTo(x, y); started = true; }
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.shadowBlur = 0;
  }
}

// Okrągły wskaźnik zegarowy (270° z polem redline)
export class DialGauge {
  constructor(canvas, { max, redFrom = Infinity, label, unit, color = '#5fd3ff', fmt = v => Math.round(v) }) {
    this.canvas = canvas;
    const dpr = 2;
    canvas.width = (canvas.clientWidth || 82) * dpr;
    canvas.height = canvas.width;
    this.ctx = canvas.getContext('2d');
    this.max = max;
    this.redFrom = redFrom;
    this.label = label;
    this.unit = unit;
    this.color = color;
    this.fmt = fmt;
    this.value = -1;
  }

  draw(v) {
    if (Math.abs(v - this.value) < this.max * 0.0005 && this.value >= 0) return;
    this.value = v;
    const { ctx } = this;
    const S = this.canvas.width, c = S / 2, r = S * 0.40;
    const A0 = Math.PI * 0.75, SWEEP = Math.PI * 1.5;
    const frac = Math.min(1, Math.max(0, v / this.max));
    ctx.clearRect(0, 0, S, S);
    ctx.lineCap = 'round';

    // tor + pole redline
    ctx.strokeStyle = 'rgba(255,255,255,0.10)';
    ctx.lineWidth = S * 0.055;
    ctx.beginPath(); ctx.arc(c, c, r, A0, A0 + SWEEP); ctx.stroke();
    if (this.redFrom < this.max) {
      ctx.strokeStyle = 'rgba(255,70,70,0.55)';
      ctx.beginPath(); ctx.arc(c, c, r, A0 + SWEEP * (this.redFrom / this.max), A0 + SWEEP); ctx.stroke();
    }

    // łuk wartości
    const over = v >= this.redFrom;
    ctx.strokeStyle = over ? '#ff5d5d' : this.color;
    ctx.beginPath(); ctx.arc(c, c, r, A0, A0 + SWEEP * frac); ctx.stroke();

    // wskazówka
    const a = A0 + SWEEP * frac;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.lineWidth = S * 0.02;
    ctx.beginPath();
    ctx.moveTo(c + Math.cos(a) * r * 0.45, c + Math.sin(a) * r * 0.45);
    ctx.lineTo(c + Math.cos(a) * r * 0.86, c + Math.sin(a) * r * 0.86);
    ctx.stroke();

    // wartość i opis
    ctx.fillStyle = over ? '#ff8484' : '#dfe6f2';
    ctx.font = `700 ${S * 0.19}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText(this.fmt(v), c, c + S * 0.10);
    ctx.fillStyle = '#8b93a2';
    ctx.font = `${S * 0.095}px monospace`;
    ctx.fillText(this.label, c, c + S * 0.36);
    ctx.fillText(this.unit, c, c + S * 0.45);
  }
}

export class TelemetryCharts {
  constructor() {
    const dpr = 2;
    const init = (id, opts) => {
      const c = document.getElementById(id);
      c.width = c.clientWidth * dpr || 506;
      c.height = c.clientHeight * dpr || 96;
      return new StripChart(c, opts);
    };
    this.thrust = init('chartThrust', { max: 2950, color: '#5fd3ff', unit: 'kN' });
    this.pc = init('chartPc', { max: 370, color: '#b18cff', unit: 'bar' });
    this.timer = 0;
    this.interval = 0.15;   // s między próbkami (~36 s okna)
  }

  update(dt, sim) {
    this.timer += dt;
    if (this.timer < this.interval) return;
    this.timer = 0;
    this.thrust.push(sim.thrust / 1000);
    this.pc.push(sim.pc / 1e5);
    this.thrust.draw();
    this.pc.draw();
  }
}

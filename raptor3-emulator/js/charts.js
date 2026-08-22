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

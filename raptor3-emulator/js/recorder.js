// Rejestrator telemetrii: próbkuje przebieg testu (10 Hz) i eksportuje CSV.

const HEADER = [
  'met_s', 'stan', 'ciag_kN', 'pc_bar', 'pompa_ch4_rpm', 'pompa_lox_rpm',
  'przeplyw_ch4_kg_s', 'przeplyw_lox_kg_s', 'isp_s', 'przepustnica_proc',
  'p_otoczenia_kPa', 'gimbal_x_deg', 'gimbal_y_deg',
];

export class Recorder {
  constructor(maxRows = 20000) {
    this.maxRows = maxRows;
    this.rows = [];
    this.active = false;
    this.timer = 0;
    this.testNo = 0;
  }

  update(dt, sim, ambientP, gx, gy) {
    if (sim.state !== 'IDLE') {
      if (!this.active) {
        this.active = true;
        this.rows = [];
        this.timer = 0;
        this.testNo += 1;
      }
      this.timer += dt;
      if (this.timer >= 0.1 && this.rows.length < this.maxRows) {
        this.timer = 0;
        this.rows.push([
          sim.met.toFixed(2),
          sim.state,
          (sim.thrust / 1000).toFixed(1),
          (sim.pc / 1e5).toFixed(1),
          Math.round(sim.fuelPump * 27000),
          Math.round(sim.oxPump * 14500),
          sim.flowCH4.toFixed(1),
          sim.flowLOX.toFixed(1),
          sim.isp > 1 ? sim.isp.toFixed(1) : '0',
          Math.round(sim.throttle * 100),
          (ambientP / 1000).toFixed(3),
          gx.toFixed(2),
          gy.toFixed(2),
        ]);
      }
    } else {
      this.active = false;
    }
  }

  toCSV() {
    return [HEADER.join(','), ...this.rows.map(r => r.join(','))].join('\n');
  }

  download() {
    if (!this.rows.length) return;
    const blob = new Blob([this.toCSV()], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `raptor3_test_${String(this.testNo).padStart(3, '0')}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
}

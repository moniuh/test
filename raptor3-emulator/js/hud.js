// Panel telemetrii — aktualizacja wskazań DOM na podstawie stanu symulacji.

import { SPEC, State, StateLabel } from './simulation.js';
import { DialGauge } from './charts.js';

const fmt0 = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 0 });
const fmt1 = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

function fmtClock(t) {
  const mm = String(Math.floor(t / 60)).padStart(2, '0');
  const ss = String(Math.floor(t % 60)).padStart(2, '0');
  const d = Math.floor((t % 1) * 10);
  return `${mm}:${ss}.${d}`;
}

const STATE_COLOR = {
  IDLE: '#8b93a2',
  PRECHILL: '#8fd8e8',
  SPINUP: '#e8c34a',
  IGNITION: '#ff8a3c',
  RAMP: '#5fd3ff',
  RUNNING: '#51e07f',
  SHUTDOWN: '#ff5d5d',
  ABORT: '#ff3b3b',
};

export class Hud {
  constructor() {
    const $ = id => document.getElementById(id);
    this.el = {
      state: $('tState'),
      thrust: $('tThrust'), thrustTf: $('tThrustTf'), thrustBar: $('bThrust'),
      flowCh4: $('tFlowCh4'), flowLox: $('tFlowLox'),
      isp: $('tIsp'),
      time: $('tTime'), prop: $('tProp'),
      ambient: $('tAmbient'),
      throttleVal: $('vThrottle'),
      altVal: $('vAlt'),
      gimbalVal: $('vGimbal'),
      btnStart: $('btnStart'), btnStop: $('btnStop'),
      met: $('tMet'), seqLog: $('seqLog'),
      alarm: $('alarm'), alarmText: $('alarmText'),
    };
    this.seqLen = -1;

    this.gPc = new DialGauge($('gPc'), {
      max: 370, redFrom: 355, label: 'CIŚN. KOMORY', unit: 'bar', color: '#b18cff',
    });
    this.gFuel = new DialGauge($('gFuel'), {
      max: 30, redFrom: 27.6, label: 'POMPA CH₄', unit: '×1000 obr/min', fmt: v => v.toFixed(1),
    });
    this.gOx = new DialGauge($('gOx'), {
      max: 16, redFrom: 14.9, label: 'POMPA LOX', unit: '×1000 obr/min', fmt: v => v.toFixed(1),
    });
  }

  setVariant(spec) {
    const pcBar = spec.pcMax / 1e5;
    this.gPc.max = Math.round(pcBar * 1.06);
    this.gPc.redFrom = Math.round(pcBar * 1.015);
    this.gPc.value = -1; // wymusza pełne przerysowanie tarczy
  }

  update(sim, ambientP, altitudeKm, gx, gy) {
    const e = this.el;

    e.state.textContent = StateLabel[sim.state];
    e.state.style.color = STATE_COLOR[sim.state];

    const thrustKN = sim.thrust / 1000;
    e.thrust.textContent = fmt0.format(thrustKN);
    e.thrustTf.textContent = fmt1.format(sim.thrust / 9.80665 / 1000);
    e.thrustBar.style.width = `${Math.min(100, thrustKN / (SPEC.thrustVac / 1000) * 100)}%`;

    this.gPc.draw(sim.pc / 1e5);
    this.gFuel.draw(sim.fuelPump * SPEC.fuelPumpMaxRPM / 1000);
    this.gOx.draw(sim.oxPump * SPEC.oxPumpMaxRPM / 1000);

    e.flowCh4.textContent = fmt0.format(sim.flowCH4);
    e.flowLox.textContent = fmt0.format(sim.flowLOX);
    e.isp.textContent = sim.isp > 1 ? fmt0.format(sim.isp) : '—';

    e.time.textContent = fmtClock(sim.burnTime);
    e.prop.textContent = fmt1.format(sim.propUsed / 1000);

    // panel sekwencji
    e.met.textContent = `T+ ${fmtClock(sim.met)}`;
    if (this.seqLen !== sim.seq.length) {
      this.seqLen = sim.seq.length;
      e.seqLog.innerHTML = sim.seq.length
        ? sim.seq.map((s, i) =>
            `<li${i === sim.seq.length - 1 ? ' class="last"' : ''}>` +
            `<span class="t">T+${s.t.toFixed(1)}s</span> ${s.label}</li>`).join('')
        : '<li class="empty">— oczekiwanie na start —</li>';
    }

    e.ambient.textContent = ambientP >= 1000
      ? `${fmt1.format(ambientP / 1000)} kPa`
      : `${fmt1.format(ambientP)} Pa`;
    e.altVal.textContent = `${fmt0.format(altitudeKm)} km`;
    const cmdPct = Math.round(sim.throttleCmd * 100);
    const actPct = Math.round(sim.throttle * 100);
    e.throttleVal.textContent = Math.abs(cmdPct - actPct) > 0
      ? `${cmdPct}% → ${actPct}%`
      : `${actPct}%`;
    e.gimbalVal.textContent = `${fmt1.format(gx)}° / ${fmt1.format(gy)}°`;

    e.btnStart.disabled = sim.state !== State.IDLE;
    e.btnStop.disabled = sim.state === State.IDLE || sim.state === State.SHUTDOWN ||
      sim.state === State.ABORT;

    // baner alarmu FDS
    const showAlarm = !!sim.alarm;
    if (e.alarm.classList.contains('show') !== showAlarm) e.alarm.classList.toggle('show', showAlarm);
    if (showAlarm) e.alarmText.textContent = `FDS · ${sim.alarm}`;
  }
}

// Panel telemetrii — aktualizacja wskazań DOM na podstawie stanu symulacji.

import { SPEC, State, StateLabel } from './simulation.js';

const fmt0 = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 0 });
const fmt1 = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

const STATE_COLOR = {
  IDLE: '#8b93a2',
  SPINUP: '#e8c34a',
  IGNITION: '#ff8a3c',
  RAMP: '#5fd3ff',
  RUNNING: '#51e07f',
  SHUTDOWN: '#ff5d5d',
};

export class Hud {
  constructor() {
    const $ = id => document.getElementById(id);
    this.el = {
      state: $('tState'),
      thrust: $('tThrust'), thrustTf: $('tThrustTf'), thrustBar: $('bThrust'),
      pc: $('tPc'), pcBar: $('bPc'),
      fuelRpm: $('tFuelRpm'), fuelBar: $('bFuel'),
      oxRpm: $('tOxRpm'), oxBar: $('bOx'),
      flowCh4: $('tFlowCh4'), flowLox: $('tFlowLox'),
      isp: $('tIsp'),
      time: $('tTime'), prop: $('tProp'),
      ambient: $('tAmbient'),
      throttleVal: $('vThrottle'),
      altVal: $('vAlt'),
      gimbalVal: $('vGimbal'),
      btnStart: $('btnStart'), btnStop: $('btnStop'),
    };
  }

  update(sim, ambientP, altitudeKm, gx, gy) {
    const e = this.el;

    e.state.textContent = StateLabel[sim.state];
    e.state.style.color = STATE_COLOR[sim.state];

    const thrustKN = sim.thrust / 1000;
    e.thrust.textContent = fmt0.format(thrustKN);
    e.thrustTf.textContent = fmt1.format(sim.thrust / 9.80665 / 1000);
    e.thrustBar.style.width = `${Math.min(100, thrustKN / (SPEC.thrustVac / 1000) * 100)}%`;

    e.pc.textContent = fmt0.format(sim.pc / 1e5);
    e.pcBar.style.width = `${Math.min(100, sim.pc / SPEC.pcMax * 100)}%`;

    const frpm = sim.fuelPump * SPEC.fuelPumpMaxRPM;
    const orpm = sim.oxPump * SPEC.oxPumpMaxRPM;
    e.fuelRpm.textContent = fmt0.format(frpm);
    e.oxRpm.textContent = fmt0.format(orpm);
    e.fuelBar.style.width = `${sim.fuelPump * 100}%`;
    e.oxBar.style.width = `${sim.oxPump * 100}%`;

    e.flowCh4.textContent = fmt0.format(sim.flowCH4);
    e.flowLox.textContent = fmt0.format(sim.flowLOX);
    e.isp.textContent = sim.isp > 1 ? fmt0.format(sim.isp) : '—';

    const t = sim.burnTime;
    const mm = String(Math.floor(t / 60)).padStart(2, '0');
    const ss = String(Math.floor(t % 60)).padStart(2, '0');
    const d = Math.floor((t % 1) * 10);
    e.time.textContent = `${mm}:${ss}.${d}`;
    e.prop.textContent = fmt1.format(sim.propUsed / 1000);

    e.ambient.textContent = ambientP >= 1000
      ? `${fmt1.format(ambientP / 1000)} kPa`
      : `${fmt1.format(ambientP)} Pa`;
    e.altVal.textContent = `${fmt0.format(altitudeKm)} km`;
    e.throttleVal.textContent = `${fmt0.format(sim.throttle * 100)}%`;
    e.gimbalVal.textContent = `${fmt1.format(gx)}° / ${fmt1.format(gy)}°`;

    e.btnStart.disabled = sim.state !== State.IDLE;
    e.btnStop.disabled = sim.state === State.IDLE || sim.state === State.SHUTDOWN;
  }
}

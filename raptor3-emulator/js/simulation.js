// Uproszczony model fizyczny silnika SpaceX Raptor 3
// (pełnoprzepływowe spalanie stopniowane, CH4/LOX).
// Wartości przybliżone, złożone z publicznie dostępnych danych.

export const g0 = 9.80665;   // m/s^2
export const P0 = 101325;    // Pa, ciśnienie na poziomie morza

export const SPEC = {
  thrustSL: 2745e3,       // N  (~280 tf na poziomie morza)
  ispVac: 350,            // s
  ispSL: 327,             // s
  pcMax: 350e5,           // Pa (~350 bar w komorze)
  minThrottle: 0.40,
  mixtureRatio: 3.6,      // O/F masowo
  exitRadius: 0.65,       // m
  exitArea: Math.PI * 0.65 * 0.65, // ~1.33 m^2
  dryMass: 1525,          // kg
  fuelPumpMaxRPM: 27000,  // szacunkowe
  oxPumpMaxRPM: 14500,    // szacunkowe
};
// Ciąg próżniowy wynika z ciągu SL + odzysku członu ciśnieniowego dyszy
SPEC.thrustVac = SPEC.thrustSL + SPEC.exitArea * P0; // ~2880 kN

export const State = {
  IDLE: 'IDLE',
  PRECHILL: 'PRECHILL',
  SPINUP: 'SPINUP',
  IGNITION: 'IGNITION',
  RAMP: 'RAMP',
  RUNNING: 'RUNNING',
  SHUTDOWN: 'SHUTDOWN',
  ABORT: 'ABORT',
};

export const StateLabel = {
  IDLE: 'GOTOWY',
  PRECHILL: 'SCHŁADZANIE',
  SPINUP: 'ROZRUCH POMP',
  IGNITION: 'ZAPŁON',
  RAMP: 'NARASTANIE CIĄGU',
  RUNNING: 'PRACA',
  SHUTDOWN: 'WYŁĄCZANIE',
  ABORT: 'AWARYJNE WYŁĄCZENIE',
};

// mapowanie przepustnicy (ułamek ciągu) -> docelowe obroty pomp
function throttleToPump(t) {
  return Math.pow(Math.max(0, t), 1 / 1.7);
}

export class EngineSim {
  constructor() {
    this.state = State.IDLE;
    this.stateT = 0;         // czas w bieżącym stanie
    this.throttle = 1.0;     // komenda 0.4..1.0
    this.ambientP = P0;      // Pa

    this.fuelPump = 0;       // 0..1
    this.oxPump = 0;         // 0..1
    this.pcFrac = 0;         // ciśnienie komory 0..1
    this.flash = 0;          // błysk zapłonu 0..1

    this.thrust = 0;         // N
    this.pc = 0;             // Pa
    this.flowCH4 = 0;        // kg/s
    this.flowLOX = 0;        // kg/s
    this.isp = 0;            // s
    this.burnTime = 0;       // s
    this.propUsed = 0;       // kg
    this.power = 0;          // 0..1 — sygnał dla grafiki/dźwięku
    this.turb = 0;           // szum turbulencji

    this.met = 0;            // zegar misji (od komendy URUCHOM)
    this.seq = [];           // log zdarzeń sekwencji: {t, label}

    this.frost = 0;          // oszronienie części kriogenicznych 0..1

    this.failure = null;     // aktywna awaria: 'ROUGH' | 'OVERSPEED'
    this.armedFailure = null;// awaria uzbrojona na następny start ('IGN_FAIL')
    this.failureT = 0;
    this.alarm = null;       // komunikat FDS (utrzymywany do następnego startu)
  }

  // wstrzyknięcie awarii testowej — rodzaj zależny od fazy pracy
  injectFailure() {
    const S = State;
    if (this.state === S.IDLE) {
      this.armedFailure = 'IGN_FAIL';
      return 'BRAK ZAPŁONU (uzbrojona na następny start)';
    }
    if (this.state === S.RUNNING || this.state === S.RAMP) {
      this.failure = Math.random() < 0.5 ? 'ROUGH' : 'OVERSPEED';
      this.failureT = 0;
      this.pushSeq('WSTRZYKNIĘTO AWARIĘ (TEST)');
      return this.failure === 'ROUGH' ? 'NIESTABILNE SPALANIE' : 'NADOBROTY POMPY CH4';
    }
    return null;
  }

  abort(reason) {
    this.pushSeq(`AWARIA: ${reason}`);
    this.pushSeq('FDS: AWARYJNE WYŁĄCZENIE');
    this.alarm = reason;
    this.failure = null;
    this.state = State.ABORT;
    this.stateT = 0;
  }

  pushSeq(label) {
    this.seq.push({ t: this.met, label });
  }

  get running() { return this.state !== State.IDLE; }
  get combusting() {
    return this.state === State.RAMP || this.state === State.RUNNING ||
      (this.state === State.IGNITION && this.stateT > 0.25) ||
      ((this.state === State.SHUTDOWN || this.state === State.ABORT) && this.pcFrac > 0.03);
  }

  start() {
    if (this.state !== State.IDLE) return;
    this.state = State.PRECHILL;
    this.stateT = 0;
    this.burnTime = 0;
    this.propUsed = 0;
    this.met = 0;
    this.seq = [];
    this.alarm = null;
    this.failure = null;
    this.pushSeq('SCHŁADZANIE WSTĘPNE');
  }

  shutdown() {
    if (this.state === State.IDLE || this.state === State.SHUTDOWN) return;
    this.state = State.SHUTDOWN;
    this.stateT = 0;
    this.pushSeq('KOMENDA WYŁĄCZENIA');
  }

  setThrottle(t) {
    this.throttle = Math.min(1, Math.max(SPEC.minThrottle, t));
  }

  update(dt) {
    this.stateT += dt;
    if (this.state !== State.IDLE) this.met += dt;
    const S = State;

    // cele pomp zależnie od stanu
    let fuelTarget = 0, oxTarget = 0;
    switch (this.state) {
      case S.IDLE:
        break;
      case S.PRECHILL: // schładzanie kriogeniczne pomp i przewodów
        if (this.stateT > 3.2) { this.state = S.SPINUP; this.stateT = 0; this.pushSeq('ROZRUCH POMP'); }
        break;
      case S.SPINUP: // rozkręcenie turbopomp (spin prime)
        fuelTarget = 0.22; oxTarget = 0.20;
        if (this.stateT > 1.6) { this.state = S.IGNITION; this.stateT = 0; this.pushSeq('ZAPŁON'); }
        break;
      case S.IGNITION: // zapłon iskrowy przedpalników i komory
        fuelTarget = 0.34; oxTarget = 0.32;
        if (this.armedFailure === 'IGN_FAIL') {
          if (this.stateT > 0.6) { this.armedFailure = null; this.abort('BRAK ZAPŁONU'); }
          break; // brak błysku — komora nie odpala
        }
        this.flash = Math.max(this.flash, Math.sin(Math.min(1, this.stateT / 0.45) * Math.PI));
        if (this.stateT > 0.55) { this.state = S.RAMP; this.stateT = 0; this.pushSeq('NARASTANIE CIĄGU'); }
        break;
      case S.RAMP: { // narastanie do zadanej przepustnicy
        const p = throttleToPump(this.throttle);
        fuelTarget = p; oxTarget = p;
        if (Math.abs(this.fuelPump - p) < 0.03 || this.stateT > 3.5) {
          this.state = S.RUNNING; this.stateT = 0;
          this.pushSeq('PRACA USTALONA');
        }
        break;
      }
      case S.RUNNING: {
        const p = throttleToPump(this.throttle);
        fuelTarget = p; oxTarget = p;
        break;
      }
      case S.SHUTDOWN:
      case S.ABORT:
        fuelTarget = 0; oxTarget = 0;
        if (this.fuelPump < 0.02 && this.pcFrac < 0.01) {
          this.state = S.IDLE; this.stateT = 0;
          this.pushSeq('SILNIK BEZPIECZNY');
        }
        break;
    }

    // aktywne awarie i nadzór FDS
    if (this.failure === 'ROUGH') {
      this.failureT += dt;
      this.turb += (Math.random() * 2 - 1) * dt * 70; // gwałtowne oscylacje spalania
      if (this.failureT > 2.2) this.abort('NIESTABILNE SPALANIE');
    } else if (this.failure === 'OVERSPEED') {
      this.failureT += dt;
      fuelTarget = Math.min(1.14, fuelTarget * (1 + this.failureT * 0.12)); // niekontrolowany wzrost
      if (this.fuelPump > 1.05) this.abort('NADOBROTY POMPY CH4');
    }

    // dynamika pomp — inercja pierwszego rzędu (LOX cięższy => wolniejszy)
    const spool = (v, target, tauUp, tauDown) => {
      const tau = target > v ? tauUp : tauDown;
      return v + (target - v) * (1 - Math.exp(-dt / tau));
    };
    const fastCut = this.state === S.ABORT; // FDS tnie zawory szybciej
    this.fuelPump = spool(this.fuelPump, fuelTarget, 0.55, fastCut ? 0.4 : 0.85);
    this.oxPump = spool(this.oxPump, oxTarget, 0.65, fastCut ? 0.45 : 0.95);

    // turbulencja spalania (delikatny szum niskoczęstotliwościowy)
    this.turb += (Math.random() * 2 - 1) * dt * 8;
    this.turb *= Math.exp(-dt * 6);

    // ciśnienie w komorze
    const pumpAvg = (this.fuelPump + this.oxPump) / 2;
    const targetPc = this.combusting ? Math.pow(pumpAvg, 1.6) : 0;
    this.pcFrac += (targetPc - this.pcFrac) * (1 - Math.exp(-dt / 0.18));
    const pcNoisy = this.pcFrac * (1 + this.turb * 0.004);

    this.pc = Math.max(0, pcNoisy) * SPEC.pcMax;

    // ciąg i przepływy
    const thrustVacNow = SPEC.thrustVac * pcNoisy;
    this.thrust = Math.max(0, thrustVacNow - SPEC.exitArea * this.ambientP * (pcNoisy > 0.02 ? 1 : 0));
    const flowTotal = thrustVacNow > 0 ? thrustVacNow / (SPEC.ispVac * g0) : 0;
    this.flowLOX = flowTotal * SPEC.mixtureRatio / (1 + SPEC.mixtureRatio);
    this.flowCH4 = flowTotal / (1 + SPEC.mixtureRatio);
    this.isp = flowTotal > 1 ? this.thrust / (flowTotal * g0) : 0;

    if (this.combusting && this.pcFrac > 0.05) this.burnTime += dt;
    this.propUsed += flowTotal * dt;

    this.flash = Math.max(0, this.flash - dt * 2.2);
    this.power = Math.max(0, Math.min(1, this.pcFrac * (1 + this.turb * 0.01)));

    // szron: szybko narasta przy schładzaniu, utrzymuje się przy pracy,
    // sublimuje po powrocie do stanu gotowości
    if (this.state === S.PRECHILL) this.frost = Math.min(1, this.frost + dt / 2.0);
    else if (this.state !== S.IDLE) this.frost = Math.min(0.9, this.frost + dt * 0.04);
    else this.frost = Math.max(0, this.frost - dt / 5);
  }
}

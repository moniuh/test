// Syntezowany dźwięk silnika: szum brązowy (huk), subbas i trzaski.
// Bez plików audio — wszystko generowane w WebAudio.

export class EngineSound {
  constructor() {
    this.ctx = null;
    this.enabled = false;
    this.level = 0;
  }

  ensure() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    const ctx = new AC();
    this.ctx = ctx;

    this.master = ctx.createGain();
    this.master.gain.value = 0;
    this.master.connect(ctx.destination);

    // szum brązowy w pętli
    const dur = 2.5;
    const buf = ctx.createBuffer(1, Math.floor(ctx.sampleRate * dur), ctx.sampleRate);
    const data = buf.getChannelData(0);
    let last = 0;
    for (let i = 0; i < data.length; i++) {
      const white = Math.random() * 2 - 1;
      last = (last + 0.02 * white) / 1.02;
      data[i] = last * 3.4;
    }
    const noise = ctx.createBufferSource();
    noise.buffer = buf;
    noise.loop = true;

    this.lp = ctx.createBiquadFilter();
    this.lp.type = 'lowpass';
    this.lp.frequency.value = 300;
    this.lp.Q.value = 0.6;

    this.noiseGain = ctx.createGain();
    this.noiseGain.gain.value = 0;
    noise.connect(this.lp).connect(this.noiseGain).connect(this.master);
    noise.start();

    // subbas ~27 Hz
    const sub = ctx.createOscillator();
    sub.type = 'sawtooth';
    sub.frequency.value = 27;
    const subLp = ctx.createBiquadFilter();
    subLp.type = 'lowpass';
    subLp.frequency.value = 90;
    this.subGain = ctx.createGain();
    this.subGain.gain.value = 0;
    sub.connect(subLp).connect(this.subGain).connect(this.master);
    sub.start();

    // trzaski spalania
    const crack = ctx.createBufferSource();
    crack.buffer = buf;
    crack.loop = true;
    crack.playbackRate.value = 1.7;
    const bp = ctx.createBiquadFilter();
    bp.type = 'bandpass';
    bp.frequency.value = 1400;
    bp.Q.value = 0.8;
    this.crackGain = ctx.createGain();
    this.crackGain.gain.value = 0;
    crack.connect(bp).connect(this.crackGain).connect(this.master);
    crack.start();

    // powolna modulacja filtra — "oddychanie" płomienia
    const lfo = ctx.createOscillator();
    lfo.frequency.value = 5.5;
    const lfoGain = ctx.createGain();
    lfoGain.gain.value = 45;
    lfo.connect(lfoGain).connect(this.lp.frequency);
    lfo.start();
  }

  setEnabled(on) {
    this.enabled = on;
    if (on) {
      this.ensure();
      if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume();
    }
  }

  update(power, dt) {
    this.level += (power - this.level) * Math.min(1, dt * 6);
    if (!this.ctx) return;
    const v = this.enabled ? this.level : 0;
    const t = this.ctx.currentTime;
    this.master.gain.setTargetAtTime(v > 0.001 ? 0.9 : 0, t, 0.08);
    this.noiseGain.gain.setTargetAtTime(v * 0.55, t, 0.06);
    this.subGain.gain.setTargetAtTime(v * 0.5, t, 0.06);
    this.crackGain.gain.setTargetAtTime(v * 0.16 * (0.6 + 0.4 * Math.random()), t, 0.05);
    this.lp.frequency.setTargetAtTime(240 + 950 * v, t, 0.1);
  }
}

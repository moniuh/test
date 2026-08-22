// Pióropusz spalin — dwa współosiowe stożki z shaderem addytywnym.
// Diamenty Macha przy wysokim ciśnieniu otoczenia, szeroka ekspansja w próżni.

import * as THREE from 'three';

const VERT = /* glsl */`
  uniform float uTime;
  uniform float uFlare;   // 0 przy p. morza .. ~1 w próżni (rozszerzenie strugi)
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  varying float vWorldY;

  void main() {
    vUv = uv;
    float down = 1.0 - uv.y;          // 0 przy wylocie dyszy -> 1 na końcu strugi
    vec3 p = position;

    // rozszerzanie strugi w próżni (brak ciśnienia otoczenia)
    float flare = 1.0 + uFlare * 3.4 * pow(down, 1.5);
    p.x *= flare;
    p.z *= flare;

    // falowanie płomienia
    float w1 = sin(uTime * 19.0 + down * 14.0) * 0.020;
    float w2 = sin(uTime * 33.0 + down * 27.0 + 1.7) * 0.013;
    p.x += (w1 + w2) * down;
    p.z += (w1 * 0.6 - w2) * down;

    vec4 world = modelMatrix * vec4(p, 1.0);
    vWorldY = world.y;
    vec4 mv = viewMatrix * world;
    vNormal = normalize(normalMatrix * normal);
    vViewDir = normalize(-mv.xyz);
    gl_Position = projectionMatrix * mv;
  }
`;

const FRAG = /* glsl */`
  uniform float uTime;
  uniform float uPower;     // 0..1 intensywność spalania
  uniform float uAmbient;   // p_otoczenia / p0 (0..1)
  uniform float uLen;       // długość strugi w metrach
  uniform float uDiamond;   // widoczność diamentów Macha
  uniform float uCore;      // 1 = stożek wewnętrzny, 0 = zewnętrzna poświata
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  varying float vWorldY;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
  }
  float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
      mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
      f.y);
  }

  void main() {
    if (uPower < 0.003) discard;
    if (vWorldY < 0.04) discard;      // przycięcie na płycie stanowiska

    float down = 1.0 - vUv.y;

    // miękkie krawędzie: jaśniej tam, gdzie powierzchnia zwrócona do kamery
    float fres = pow(abs(dot(normalize(vNormal), normalize(vViewDir))), 1.25);

    // smugi przepływu zbiegające w dół strugi
    float n = noise(vec2(vUv.x * 22.0, down * 7.0 - uTime * 10.0));
    n = 0.55 + 0.45 * n;

    // zanik wzdłuż osi — w próżni struga rzednie szybciej, ale jest szersza
    float axial = exp(-down * mix(2.2, 1.35, uAmbient));
    axial *= smoothstep(0.0, 0.045, down * 0.5 + 0.03); // domknięcie przy wylocie

    // diamenty Macha — okresowe komórki uderzeniowe, gasnące wzdłuż strugi
    float cells = abs(sin(down * uLen * 3.6 - 0.55));
    float dia = pow(cells, 34.0) * exp(-down * 2.4) * uDiamond;

    float b = (axial * n * 0.78 + dia * 2.6) * fres * uPower;
    b *= mix(0.30, 1.0, uCore);

    // kolory metaloksu: fiolet na obrzeżu -> błękit -> biało-niebieskie jądro
    vec3 violet = vec3(0.42, 0.20, 0.85);
    vec3 blue   = vec3(0.25, 0.52, 1.00);
    vec3 core   = vec3(0.95, 0.97, 1.00);
    float t = clamp(b * 1.35, 0.0, 1.0);
    vec3 col = mix(violet, blue, t);
    col = mix(col, core, pow(clamp(b, 0.0, 1.0), 2.1));

    gl_FragColor = vec4(col * b * 2.5, 1.0);
  }
`;

function makeMaterial(core) {
  return new THREE.ShaderMaterial({
    vertexShader: VERT,
    fragmentShader: FRAG,
    uniforms: {
      uTime: { value: 0 },
      uPower: { value: 0 },
      uAmbient: { value: 1 },
      uLen: { value: 5 },
      uFlare: { value: 0 },
      uDiamond: { value: 0 },
      uCore: { value: core },
    },
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    side: THREE.DoubleSide,
  });
}

export function makeRadialTexture(size = 256, inner = 'rgba(255,255,255,1)', mid = 'rgba(160,190,255,0.35)') {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const ctx = c.getContext('2d');
  const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  g.addColorStop(0, inner);
  g.addColorStop(0.35, mid);
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, size, size);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export class Plume {
  constructor() {
    this.group = new THREE.Group();

    // geometrie: jednostkowa wysokość 1, wylot w y=0, struga w -y
    const gInner = new THREE.CylinderGeometry(0.58, 0.30, 1, 48, 40, true);
    gInner.translate(0, -0.5, 0);
    const gOuter = new THREE.CylinderGeometry(0.72, 0.80, 1, 48, 40, true);
    gOuter.translate(0, -0.5, 0);

    this.matInner = makeMaterial(1);
    this.matOuter = makeMaterial(0);

    this.inner = new THREE.Mesh(gInner, this.matInner);
    this.outer = new THREE.Mesh(gOuter, this.matOuter);
    this.inner.frustumCulled = false;
    this.outer.frustumCulled = false;
    this.group.add(this.inner, this.outer);

    // błysk zapłonu
    const flashMat = new THREE.SpriteMaterial({
      map: makeRadialTexture(256, 'rgba(255,235,200,1)', 'rgba(255,150,60,0.5)'),
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      transparent: true,
      opacity: 0,
    });
    this.flash = new THREE.Sprite(flashMat);
    this.flash.position.set(0, -0.25, 0);
    this.group.add(this.flash);
  }

  // t: czas [s], sim: EngineSim, ambientFrac: p/p0
  update(t, sim, ambientFrac) {
    const power = sim.power;
    const expand = 1 - Math.min(1, Math.pow(Math.max(ambientFrac, 1e-6), 0.35));
    const len = (4.6 + 4.4 * expand) * (0.35 + 0.65 * power);
    const dia = Math.pow(Math.max(ambientFrac, 0), 0.6) * power;

    for (const m of [this.matInner, this.matOuter]) {
      const u = m.uniforms;
      u.uTime.value = t;
      u.uPower.value = power * (1 + sim.turb * 0.02);
      u.uAmbient.value = ambientFrac;
      u.uLen.value = len;
      u.uFlare.value = expand;
      u.uDiamond.value = dia;
    }
    this.inner.scale.set(1, Math.max(len, 0.001), 1);
    this.outer.scale.set(1, Math.max(len * 1.06, 0.001), 1);
    this.inner.visible = this.outer.visible = power > 0.004;

    const f = sim.flash;
    this.flash.material.opacity = f * 0.9;
    const s = 1.5 + 3.5 * f;
    this.flash.scale.set(s, s, 1);
  }
}

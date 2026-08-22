// Lekki system cząsteczek GPU (THREE.Points + shader) do oparów, dymu i pary.
// Bufor pierścieniowy — emisja nadpisuje najstarsze cząstki.

import * as THREE from 'three';
import { makeRadialTexture } from './plume.js';

const VERT = /* glsl */`
  attribute float aBirth;
  attribute float aLife;
  attribute float aSize;
  attribute float aSeed;
  attribute vec3 aVel;
  uniform float uTime;
  uniform float uScale;
  varying float vFade;

  void main() {
    float age = uTime - aBirth;
    float k = age / aLife;
    if (age < 0.0 || k >= 1.0) {
      gl_Position = vec4(0.0, 0.0, -1e6, 1.0);
      gl_PointSize = 0.0;
      vFade = 0.0;
      return;
    }
    vec3 p = position + aVel * age;
    // dryf boczny (wiatr/zawirowania)
    p.x += sin(aSeed * 6.2831 + age * 1.4) * 0.16 * age;
    p.z += cos(aSeed * 5.1 + age * 1.1) * 0.14 * age;

    vFade = smoothstep(0.0, 0.12, k) * (1.0 - smoothstep(0.5, 1.0, k));
    float s = aSize * (0.55 + 2.1 * k);
    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_PointSize = s * uScale / max(0.1, -mv.z);
    gl_Position = projectionMatrix * mv;
  }
`;

const FRAG = /* glsl */`
  uniform sampler2D uMap;
  uniform vec3 uColor;
  uniform float uOpacity;
  varying float vFade;

  void main() {
    vec4 tex = texture2D(uMap, gl_PointCoord);
    float a = tex.a * vFade * uOpacity;
    if (a < 0.004) discard;
    gl_FragColor = vec4(uColor, a);
  }
`;

export class PuffSystem {
  constructor({ count = 600, color = 0xdfeaf5, opacity = 0.5, additive = false } = {}) {
    this.count = count;
    this.head = 0;

    const geo = new THREE.BufferGeometry();
    const mk = (n) => new THREE.BufferAttribute(new Float32Array(count * n), n).setUsage(THREE.DynamicDrawUsage);
    this.aPos = mk(3);
    this.aVel = mk(3);
    this.aBirth = mk(1);
    this.aLife = mk(1);
    this.aSize = mk(1);
    this.aSeed = mk(1);
    this.aBirth.array.fill(-1e6);
    geo.setAttribute('position', this.aPos);
    geo.setAttribute('aVel', this.aVel);
    geo.setAttribute('aBirth', this.aBirth);
    geo.setAttribute('aLife', this.aLife);
    geo.setAttribute('aSize', this.aSize);
    geo.setAttribute('aSeed', this.aSeed);

    this.mat = new THREE.ShaderMaterial({
      vertexShader: VERT,
      fragmentShader: FRAG,
      uniforms: {
        uTime: { value: 0 },
        uScale: { value: 320 },
        uMap: { value: makeRadialTexture(128, 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0.35)') },
        uColor: { value: new THREE.Color(color) },
        uOpacity: { value: opacity },
      },
      transparent: true,
      depthWrite: false,
      blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
    });

    this.points = new THREE.Points(geo, this.mat);
    this.points.frustumCulled = false;
  }

  emit(pos, vel, life, size, time) {
    const i = this.head;
    this.head = (this.head + 1) % this.count;
    this.aPos.array.set([pos.x, pos.y, pos.z], i * 3);
    this.aVel.array.set([vel.x, vel.y, vel.z], i * 3);
    this.aBirth.array[i] = time;
    this.aLife.array[i] = life;
    this.aSize.array[i] = size;
    this.aSeed.array[i] = Math.random();
    this.dirty = true;
  }

  update(time) {
    this.mat.uniforms.uTime.value = time;
    if (this.dirty) {
      this.dirty = false;
      for (const a of [this.aPos, this.aVel, this.aBirth, this.aLife, this.aSize, this.aSeed]) {
        a.needsUpdate = true;
      }
    }
  }
}

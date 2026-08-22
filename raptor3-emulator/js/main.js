// Emulator silnika Raptor 3 — scena, sterowanie, pętla renderowania.

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { buildEngine, buildStand, buildGround, buildStars, Strut } from './engineModel.js';
import { EngineSim, P0 } from './simulation.js';
import { Plume } from './plume.js';
import { Hud } from './hud.js';
import { EngineSound } from './sound.js';

const PIVOT_WORLD_Y = 5.45; // wysokość przegubu gimbala nad płytą

// ---------- renderer / scena ----------

const canvas = document.getElementById('scene');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.12;

const scene = new THREE.Scene();
const SKY_SL = new THREE.Color(0x101827);
const SKY_VAC = new THREE.Color(0x010208);
scene.background = SKY_SL.clone();
scene.fog = new THREE.FogExp2(SKY_SL.clone(), 0.012);

const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 500);
camera.position.set(11.2, 4.4, 2.6);

const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, 3.4, 0);
controls.enableDamping = true;
controls.dampingFactor = 0.06;
controls.minDistance = 3;
controls.maxDistance = 70;
controls.maxPolarAngle = Math.PI * 0.55;

// odbicia otoczenia dla metali
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
if ('environmentIntensity' in scene) scene.environmentIntensity = 0.35;

// światła
scene.add(new THREE.HemisphereLight(0x8fa8cc, 0x1a1c20, 0.5));
const flood1 = new THREE.SpotLight(0xfff2dd, 620, 0, 0.5, 0.45, 1.8);
flood1.position.set(9, 8, 7);
flood1.target.position.set(0, 3, 0);
scene.add(flood1, flood1.target);
const flood2 = new THREE.SpotLight(0xdde8ff, 340, 0, 0.55, 0.5, 1.8);
flood2.position.set(-8, 9, -6);
flood2.target.position.set(0, 3.5, 0);
scene.add(flood2, flood2.target);

const plumeLight = new THREE.PointLight(0x86b4ff, 0, 60, 1.8);
scene.add(plumeLight);
const throatLight = new THREE.PointLight(0xffc890, 0, 12, 2.0);
scene.add(throatLight);

// ---------- obiekty ----------

const engine = buildEngine();
engine.group.position.y = PIVOT_WORLD_Y;
scene.add(engine.group);

const stand = buildStand(PIVOT_WORLD_Y);
scene.add(stand.group);

const ground = buildGround();
scene.add(ground.group);

const stars = buildStars();
scene.add(stars.points);

const plume = new Plume();
plume.group.position.y = engine.exitLocalY;
engine.group.add(plume.group);

// siłowniki gimbala: stanowisko -> kołnierz komory
const actuators = [new Strut(0.045, engineStrutMat(), true), new Strut(0.045, engineStrutMat(), true)];
function engineStrutMat() {
  return new THREE.MeshStandardMaterial({ color: 0xb9bec6, metalness: 0.9, roughness: 0.3 });
}
for (const a of actuators) scene.add(a.mesh);

// ---------- symulacja i UI ----------

const sim = new EngineSim();
const hud = new Hud();
const sound = new EngineSound();
window.__sim = sim; // hak diagnostyczny (testy/konsola)

let altitudeKm = 0;
let ambientP = P0;
let gimbalX = 0, gimbalY = 0;           // aktualne wychylenie [deg]
let gimbalTX = 0, gimbalTY = 0;         // cel [deg]
const GIMBAL_MAX = 8;

const $ = id => document.getElementById(id);
const elThrottle = $('throttle');
const elAlt = $('altitude');
const elGx = $('gimbalX');
const elGy = $('gimbalY');

$('btnStart').addEventListener('click', () => { sim.start(); sound.ensure(); if (sound.ctx && sound.ctx.state === 'suspended') sound.ctx.resume(); });
$('btnStop').addEventListener('click', () => sim.shutdown());
$('btnCenter').addEventListener('click', () => { gimbalTX = 0; gimbalTY = 0; elGx.value = 0; elGy.value = 0; });

const btnSound = $('btnSound');
btnSound.addEventListener('click', () => {
  sound.setEnabled(!sound.enabled);
  btnSound.textContent = sound.enabled ? 'DŹWIĘK: WŁ' : 'DŹWIĘK: WYŁ';
  btnSound.classList.toggle('on', sound.enabled);
});

elThrottle.addEventListener('input', () => sim.setThrottle(elThrottle.value / 100));
sim.setThrottle(elThrottle.value / 100);

function applyAltitude() {
  altitudeKm = Number(elAlt.value);
  ambientP = P0 * Math.exp(-altitudeKm / 7.16); // atmosfera izotermiczna
  if (altitudeKm >= 99.5) ambientP = 0;
  sim.ambientP = ambientP;
}
elAlt.addEventListener('input', applyAltitude);
applyAltitude();

elGx.addEventListener('input', () => { gimbalTX = Number(elGx.value); });
elGy.addEventListener('input', () => { gimbalTY = Number(elGy.value); });

// strzałki — sterowanie gimbalem z klawiatury
const keys = new Set();
window.addEventListener('keydown', e => {
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
    keys.add(e.key);
    e.preventDefault();
  }
});
window.addEventListener('keyup', e => keys.delete(e.key));

// ---------- pętla ----------

const _a = new THREE.Vector3();
const clock = new THREE.Clock();
let shake = 0;

function animate() {
  requestAnimationFrame(animate);
  const dt = Math.min(clock.getDelta(), 0.05);
  const t = clock.elapsedTime;

  // klawiatura -> cel gimbala
  const rate = 14 * dt;
  if (keys.has('ArrowLeft')) gimbalTY = Math.max(-GIMBAL_MAX, gimbalTY - rate);
  if (keys.has('ArrowRight')) gimbalTY = Math.min(GIMBAL_MAX, gimbalTY + rate);
  if (keys.has('ArrowUp')) gimbalTX = Math.max(-GIMBAL_MAX, gimbalTX - rate);
  if (keys.has('ArrowDown')) gimbalTX = Math.min(GIMBAL_MAX, gimbalTX + rate);
  if (keys.size) { elGx.value = gimbalTX; elGy.value = gimbalTY; }

  gimbalX += (gimbalTX - gimbalX) * Math.min(1, dt * 6);
  gimbalY += (gimbalTY - gimbalY) * Math.min(1, dt * 6);
  engine.group.rotation.set(
    THREE.MathUtils.degToRad(gimbalX),
    0,
    THREE.MathUtils.degToRad(-gimbalY),
  );

  sim.update(dt);

  // siłowniki podążają za kołnierzem
  for (let i = 0; i < 2; i++) {
    _a.copy(engine.actAnchorsLocal[i]);
    engine.group.localToWorld(_a);
    actuators[i].update(stand.actAnchorsWorld[i], _a);
  }

  // wysokość -> niebo, mgła, gwiazdy
  const skyT = THREE.MathUtils.clamp(altitudeKm / 55, 0, 1);
  scene.background.copy(SKY_SL).lerp(SKY_VAC, skyT);
  scene.fog.color.copy(scene.background);
  scene.fog.density = 0.012 * (1 - skyT);
  stars.mat.opacity = THREE.MathUtils.clamp((altitudeKm - 22) / 45, 0, 1);

  // pióropusz i oświetlenie dynamiczne
  const ambientFrac = ambientP / P0;
  plume.update(t, sim, ambientFrac);

  const p = sim.power;
  const flick = 0.9 + 0.1 * Math.sin(t * 47) * Math.sin(t * 31 + 1.3);
  plumeLight.intensity = p * 620 * flick + sim.flash * 700;
  _a.set(0, -1.6, 0);
  engine.group.localToWorld(_a.add(plume.group.position));
  plumeLight.position.copy(_a);
  plumeLight.color.setHSL(0.62, 0.7, 0.6 + 0.15 * p);

  throatLight.intensity = p * 260 + sim.flash * 400;
  _a.set(0, engine.exitLocalY + 0.4, 0);
  engine.group.localToWorld(_a);
  throatLight.position.copy(_a);

  // poświata wnętrza dyszy
  const g = Math.min(1.2, p * 1.25 + sim.flash * 0.6);
  engine.glowMat.color.setRGB(2.4 * g, 0.9 * Math.pow(g, 1.4), 0.45 * Math.pow(g, 1.8));

  // odblask na płycie — tylko gdy struga sięga ziemi (niskie wysokości)
  ground.glowMat.opacity = p * 0.32 * THREE.MathUtils.clamp(ambientFrac * 3, 0.06, 1);

  // drgania kamery przy pracy silnika
  shake += ((p * 0.02 + sim.flash * 0.045) - shake) * Math.min(1, dt * 8);
  controls.update();
  const ox = (Math.random() - 0.5) * shake;
  const oy = (Math.random() - 0.5) * shake;
  const oz = (Math.random() - 0.5) * shake;
  camera.position.x += ox; camera.position.y += oy; camera.position.z += oz;

  hud.update(sim, ambientP, altitudeKm, gimbalX, gimbalY);
  sound.update(p, dt);

  renderer.render(scene, camera);
  camera.position.x -= ox; camera.position.y -= oy; camera.position.z -= oz;
}

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

animate();

// Proceduralna geometria: silnik Raptor 3, stanowisko testowe, podłoże.
// Wymiary przybliżone do rzeczywistych (wysokość silnika ~3.1 m, dysza ~1.3 m).

import * as THREE from 'three';
import { makeRadialTexture } from './plume.js';

const PIVOT = 3.55; // punkt przegubu gimbala w układzie silnika (y od wylotu dyszy)

const MAT = {
  steel: new THREE.MeshStandardMaterial({ color: 0x9aa0a8, metalness: 0.92, roughness: 0.3 }),
  darkSteel: new THREE.MeshStandardMaterial({ color: 0x42474f, metalness: 0.85, roughness: 0.45 }),
  chamber: new THREE.MeshStandardMaterial({ color: 0x77594a, metalness: 0.85, roughness: 0.48 }),
  pipe: new THREE.MeshStandardMaterial({ color: 0xb7bcc4, metalness: 0.95, roughness: 0.24 }),
  stand: new THREE.MeshStandardMaterial({ color: 0x23262c, metalness: 0.55, roughness: 0.72 }),
  concrete: new THREE.MeshStandardMaterial({ color: 0x2c2f35, metalness: 0.0, roughness: 0.95 }),
};

// ---------- profil dyszy / komory ----------

function radiusAt(y) {
  if (y > 2.72) { // kopuła wtryskiwacza (ćwierćelipsa)
    const t = (y - 2.72) / 0.30;
    return 0.44 * Math.sqrt(Math.max(0, 1 - t * t));
  }
  if (y > 1.98) return 0.44;                       // komora spalania
  if (y > 1.52) {                                  // zbieżna część do gardzieli
    const t = (1.98 - y) / 0.46;
    return 0.155 + (0.44 - 0.155) * (0.5 + 0.5 * Math.cos(Math.PI * t));
  }
  const t = (1.52 - y) / 1.52;                     // dzwon dyszy
  return 0.155 + (0.66 - 0.155) * Math.pow(t, 0.72);
}

function latheBetween(yTop, yBottom, steps, material) {
  const pts = [];
  for (let i = 0; i <= steps; i++) {
    const y = yTop + (yBottom - yTop) * (i / steps);
    pts.push(new THREE.Vector2(Math.max(radiusAt(y), 0.004), y));
  }
  const geo = new THREE.LatheGeometry(pts, 96);
  return new THREE.Mesh(geo, material);
}

// tekstura dzwonu: pionowe kanały regeneracyjne + przebarwienia termiczne
function makeBellTexture() {
  const c = document.createElement('canvas');
  c.width = 1024; c.height = 512;
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#8f959d';
  ctx.fillRect(0, 0, 1024, 512);
  for (let x = 0; x < 1024; x += 5) {
    ctx.fillStyle = 'rgba(0,0,0,0.16)';
    ctx.fillRect(x, 0, 1, 512);
    if (x % 40 === 0) {
      ctx.fillStyle = 'rgba(0,0,0,0.30)';
      ctx.fillRect(x, 0, 2, 512);
      ctx.fillStyle = 'rgba(255,255,255,0.10)';
      ctx.fillRect(x + 2, 0, 1, 512);
    }
  }
  // przebarwienia cieplne najsilniejsze przy gardzieli (dół canvasa = v=0 = gardziel)
  const g = ctx.createLinearGradient(0, 512, 0, 0);
  g.addColorStop(0.00, 'rgba(205,140,70,0.50)');
  g.addColorStop(0.14, 'rgba(120,95,205,0.30)');
  g.addColorStop(0.32, 'rgba(70,85,130,0.14)');
  g.addColorStop(0.55, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 1024, 512);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = THREE.RepeatWrapping;
  return tex;
}

function ring(radius, tube, y, material, segments = 64) {
  const m = new THREE.Mesh(new THREE.TorusGeometry(radius, tube, 12, segments), material);
  m.rotation.x = Math.PI / 2;
  m.position.y = y;
  return m;
}

function tube(points, radius, material) {
  const curve = new THREE.CatmullRomCurve3(points.map(p => new THREE.Vector3(...p)));
  return new THREE.Mesh(new THREE.TubeGeometry(curve, 32, radius, 12, false), material);
}

// ---------- siłownik / zastrzał między dwoma punktami ----------

const _up = new THREE.Vector3(0, 1, 0);
const _dir = new THREE.Vector3();

export class Strut {
  constructor(radius, material, withEnds = false) {
    this.mesh = new THREE.Group();
    this.body = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, 1, 12), material);
    this.mesh.add(this.body);
    if (withEnds) {
      const sg = new THREE.SphereGeometry(radius * 1.7, 12, 10);
      this.e1 = new THREE.Mesh(sg, material);
      this.e2 = new THREE.Mesh(sg, material);
      this.mesh.add(this.e1, this.e2);
    }
  }
  update(a, b) {
    _dir.subVectors(b, a);
    const len = Math.max(_dir.length(), 0.001);
    this.body.scale.set(1, len, 1);
    this.body.position.copy(a).addScaledVector(_dir, 0.5);
    this.body.quaternion.setFromUnitVectors(_up, _dir.normalize());
    if (this.e1) { this.e1.position.copy(a); this.e2.position.copy(b); }
  }
}

// ---------- silnik ----------

export function buildEngine() {
  const group = new THREE.Group(); // origin = przegub gimbala
  const body = new THREE.Group();
  body.position.y = -PIVOT;
  group.add(body);

  // części kriogeniczne dostają własne materiały — main.js nakłada na nie szron
  const cryoParts = [];
  const markCryo = (mesh, strength) => {
    mesh.material = mesh.material.clone();
    mesh.userData.cryoIdx = cryoParts.length;
    cryoParts.push({
      mat: mesh.material,
      base: mesh.material.color.clone(),
      baseRough: mesh.material.roughness,
      baseMetal: mesh.material.metalness,
      strength,
    });
  };

  // górna część: kopuła + komora + zbieżna
  const upper = latheBetween(3.015, 1.52, 60, MAT.chamber);
  body.add(upper);

  // dzwon dyszy z teksturą kanałów
  const bellMat = new THREE.MeshStandardMaterial({
    color: 0xffffff, metalness: 0.9, roughness: 0.34,
    map: makeBellTexture(), side: THREE.DoubleSide,
  });
  const bell = latheBetween(1.52, 0.0, 72, bellMat);
  body.add(bell);

  // wewnętrzna powierzchnia dyszy — poświata cieplna sterowana z symulacji
  const glowMat = new THREE.MeshBasicMaterial({ color: 0x000000, side: THREE.DoubleSide });
  const glow = latheBetween(1.52, 0.015, 40, glowMat);
  glow.scale.set(0.982, 1, 0.982);
  glow.userData.glow = true; // referencja odtwarzana po klonowaniu materiału (przekrój)
  body.add(glow);

  // pierścienie usztywniające i kołnierze
  body.add(ring(radiusAt(0.30) + 0.012, 0.022, 0.30, MAT.steel));
  body.add(ring(radiusAt(0.78) + 0.012, 0.022, 0.78, MAT.steel));
  body.add(ring(radiusAt(1.18) + 0.012, 0.020, 1.18, MAT.steel));
  body.add(ring(0.665, 0.030, 0.015, MAT.darkSteel));       // krawędź wylotu
  body.add(ring(0.470, 0.050, 1.98, MAT.darkSteel));        // kołnierz komory
  body.add(ring(0.300, 0.045, 2.90, MAT.darkSteel));        // kołnierz głowicy

  // turbopompy (CH4 i LOX) po bokach komory; LOX (+x) szroni mocniej
  for (const sx of [-1, 1]) {
    const cryoStr = sx > 0 ? 1.0 : 0.62;
    const pump = new THREE.Group();
    const housing = new THREE.Mesh(new THREE.CylinderGeometry(0.185, 0.185, 0.78, 28), MAT.steel);
    housing.position.y = 2.35;
    markCryo(housing, cryoStr);
    const volute = ring(0.185, 0.075, 2.02, MAT.darkSteel, 40);
    markCryo(volute, cryoStr * 0.85);
    const cap = new THREE.Mesh(new THREE.SphereGeometry(0.185, 28, 14, 0, Math.PI * 2, 0, Math.PI / 2), MAT.steel);
    cap.position.y = 2.74;
    markCryo(cap, cryoStr);
    const preburner = new THREE.Mesh(new THREE.CylinderGeometry(0.125, 0.150, 0.34, 24), MAT.darkSteel);
    preburner.position.y = 2.95;
    const pcap = new THREE.Mesh(new THREE.SphereGeometry(0.125, 20, 12), MAT.darkSteel);
    pcap.position.y = 3.12;
    pump.add(housing, volute, cap, preburner, pcap);
    pump.position.x = sx * 0.60;
    body.add(pump);

    // kanał z przedpalnika do głowicy komory
    body.add(tube([
      [sx * 0.60, 3.16, 0], [sx * 0.50, 3.30, 0], [sx * 0.22, 3.34, 0], [sx * 0.10, 3.20, 0],
    ], 0.068, MAT.pipe));
    // zasilanie pompy z góry (przegub elastyczny przy gimbalu)
    const feed = tube([
      [sx * 0.60, 2.74, 0.0], [sx * 0.62, 3.10, 0.14], [sx * 0.45, 3.42, 0.10], [sx * 0.22, 3.52, 0.0],
    ], 0.075, MAT.steel);
    markCryo(feed, cryoStr * 0.9);
    body.add(feed);
    // linia recyrkulacji wzdłuż komory
    body.add(tube([
      [sx * 0.46, 2.0, sx * 0.22], [sx * 0.40, 1.62, sx * 0.26], [sx * 0.24, 1.45, sx * 0.14],
    ], 0.030, MAT.pipe));
  }

  // płyta wtryskiwacza — widoczna tylko w widoku przekroju
  const injCanvas = document.createElement('canvas');
  injCanvas.width = injCanvas.height = 256;
  const ictx = injCanvas.getContext('2d');
  ictx.fillStyle = '#4a4038';
  ictx.fillRect(0, 0, 256, 256);
  ictx.fillStyle = '#2b241f';
  for (let ring = 1; ring <= 5; ring++) {
    const n = ring * 10;
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2;
      const rr = ring * 22;
      ictx.beginPath();
      ictx.arc(128 + Math.cos(a) * rr, 128 + Math.sin(a) * rr, 3.4, 0, Math.PI * 2);
      ictx.fill();
    }
  }
  const injTex = new THREE.CanvasTexture(injCanvas);
  injTex.colorSpace = THREE.SRGBColorSpace;
  const injector = new THREE.Mesh(
    new THREE.CircleGeometry(0.42, 48),
    new THREE.MeshStandardMaterial({ map: injTex, roughness: 0.7, metalness: 0.4, side: THREE.DoubleSide })
  );
  injector.rotation.x = -Math.PI / 2;
  injector.position.y = 2.56;
  body.add(injector);
  const torch = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.22, 12), MAT.darkSteel);
  torch.position.y = 2.62;
  body.add(torch);

  // centralny kanał LOX przez oś gimbala
  const mainDuct = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.19, 0.55, 24), MAT.steel);
  mainDuct.position.y = 3.28;
  markCryo(mainDuct, 1.0);
  body.add(mainDuct);

  // zewnętrzne orurowanie starszych wariantów (R1 najgęstsze, R2 częściowe)
  const plumbingR1 = new THREE.Group();
  plumbingR1.add(
    tube([[0.50, 2.40, 0.30], [0.05, 2.22, 0.56], [-0.48, 2.05, 0.32], [-0.56, 1.75, -0.18], [-0.05, 1.62, -0.52], [0.48, 1.58, -0.15]], 0.034, MAT.pipe),
    tube([[0.30, 1.55, 0.38], [0.52, 1.05, 0.40], [0.58, 0.55, 0.34], [0.50, 0.25, 0.20]], 0.030, MAT.pipe),
    tube([[-0.34, 1.55, -0.34], [-0.52, 1.10, -0.38], [-0.60, 0.60, -0.30]], 0.030, MAT.pipe),
    tube([[0.15, 2.95, -0.35], [0.35, 2.60, -0.48], [0.42, 2.15, -0.42], [0.35, 1.90, -0.30]], 0.040, MAT.steel),
  );
  const boxCtl = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.32, 0.13), MAT.darkSteel);
  boxCtl.position.set(-0.48, 2.35, -0.28);
  plumbingR1.add(boxCtl);

  const plumbingR2 = new THREE.Group();
  plumbingR2.add(
    tube([[0.42, 2.15, 0.30], [0.10, 1.95, 0.50], [-0.35, 1.80, 0.35], [-0.45, 1.60, 0.10]], 0.036, MAT.pipe),
    tube([[-0.15, 2.95, 0.32], [-0.35, 2.55, 0.44], [-0.42, 2.15, 0.38]], 0.040, MAT.steel),
  );
  plumbingR1.visible = false;
  plumbingR2.visible = false;
  body.add(plumbingR1, plumbingR2);

  // blok gimbala i przegub kulowy
  const block = new THREE.Mesh(new THREE.BoxGeometry(0.46, 0.30, 0.46), MAT.darkSteel);
  block.position.y = 3.42;
  body.add(block);
  const joint = new THREE.Mesh(new THREE.SphereGeometry(0.14, 24, 16), MAT.steel);
  joint.position.y = PIVOT;
  body.add(joint);

  // punkty mocowania siłowników na kołnierzu komory (układ silnika)
  const actAnchorsLocal = [
    new THREE.Vector3(0.47, 2.55 - PIVOT, 0),
    new THREE.Vector3(0, 2.55 - PIVOT, 0.47),
  ];

  return { group, glowMat, actAnchorsLocal, cryoParts, plumbingR1, plumbingR2, exitLocalY: -PIVOT };
}

// ---------- stanowisko testowe ----------

export function buildStand(pivotWorldY) {
  const group = new THREE.Group();
  const topY = pivotWorldY + 0.42;

  const platform = new THREE.Mesh(new THREE.BoxGeometry(3.6, 0.34, 3.6), MAT.stand);
  platform.position.y = topY + 0.17;
  group.add(platform);

  const legPositions = [[-1.55, -1.55], [1.55, -1.55], [-1.55, 1.55], [1.55, 1.55]];
  for (const [x, z] of legPositions) {
    const leg = new THREE.Mesh(new THREE.BoxGeometry(0.22, topY, 0.22), MAT.stand);
    leg.position.set(x, topY / 2, z);
    group.add(leg);
    const foot = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.1, 0.5), MAT.darkSteel);
    foot.position.set(x, 0.05, z);
    group.add(foot);
  }

  // stężenia X na czterech ścianach kratownicy
  const braces = [];
  const h1 = 0.3, h2 = topY - 0.5;
  const faces = [
    [[-1.55, -1.55], [1.55, -1.55]],
    [[-1.55, 1.55], [1.55, 1.55]],
    [[-1.55, -1.55], [-1.55, 1.55]],
    [[1.55, -1.55], [1.55, 1.55]],
  ];
  for (const [[ax, az], [bx, bz]] of faces) {
    for (const [ya, yb] of [[h1, h2], [h2, h1]]) {
      const s = new Strut(0.03, MAT.stand);
      s.update(new THREE.Vector3(ax, ya, az), new THREE.Vector3(bx, yb, bz));
      group.add(s.mesh);
      braces.push(s);
    }
  }

  // pierścień montażowy pod platformą
  const mountRing = ring(1.30, 0.07, pivotWorldY + 0.12, MAT.darkSteel, 64);
  group.add(mountRing);
  for (let i = 0; i < 4; i++) {
    const a = i * Math.PI / 2 + Math.PI / 4;
    const s = new Strut(0.05, MAT.darkSteel);
    s.update(
      new THREE.Vector3(Math.cos(a) * 1.30, pivotWorldY + 0.12, Math.sin(a) * 1.30),
      new THREE.Vector3(Math.cos(a) * 1.30, topY, Math.sin(a) * 1.30),
    );
    group.add(s.mesh);
  }

  // wsporniki siłowników gimbala
  const actAnchorsWorld = [
    new THREE.Vector3(1.30, pivotWorldY + 0.05, 0),
    new THREE.Vector3(0, pivotWorldY + 0.05, 1.30),
  ];
  for (const p of actAnchorsWorld) {
    const b = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.22, 0.16), MAT.darkSteel);
    b.position.copy(p);
    group.add(b);
  }

  return { group, actAnchorsWorld };
}

// ---------- podłoże ----------

export function buildGround() {
  const group = new THREE.Group();

  const pad = new THREE.Mesh(new THREE.CircleGeometry(45, 72), MAT.concrete);
  pad.rotation.x = -Math.PI / 2;
  group.add(pad);

  const grid = new THREE.GridHelper(90, 45, 0x3a414c, 0x2b3037);
  grid.position.y = 0.012;
  if (grid.material) { grid.material.transparent = true; grid.material.opacity = 0.5; }
  group.add(grid);

  const scorch = new THREE.Mesh(
    new THREE.CircleGeometry(2.6, 48),
    new THREE.MeshStandardMaterial({ color: 0x141518, roughness: 1 })
  );
  scorch.rotation.x = -Math.PI / 2;
  scorch.position.y = 0.016;
  group.add(scorch);

  const glowMat = new THREE.MeshBasicMaterial({
    map: makeRadialTexture(256, 'rgba(200,220,255,1)', 'rgba(120,160,255,0.4)'),
    blending: THREE.AdditiveBlending,
    transparent: true,
    depthWrite: false,
    opacity: 0,
  });
  const glowDisc = new THREE.Mesh(new THREE.CircleGeometry(3.6, 48), glowMat);
  glowDisc.rotation.x = -Math.PI / 2;
  glowDisc.position.y = 0.02;
  group.add(glowDisc);

  return { group, glowMat };
}

// ---------- gwiazdy (tryb dużej wysokości) ----------

export function buildStars() {
  const N = 1600;
  const pos = new Float32Array(N * 3);
  for (let i = 0; i < N; i++) {
    const v = new THREE.Vector3().randomDirection().multiplyScalar(160);
    if (v.y < 2) v.y = Math.abs(v.y) + 2;
    pos.set([v.x, v.y, v.z], i * 3);
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const mat = new THREE.PointsMaterial({
    color: 0xcfd8ff, size: 1.5, sizeAttenuation: false,
    transparent: true, opacity: 0, depthWrite: false,
  });
  const points = new THREE.Points(geo, mat);
  points.frustumCulled = false;
  return { points, mat };
}

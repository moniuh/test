"use strict";

// --- stan i narzędzia ---------------------------------------------------------
const state = { config: null, health: null, jobs: [], jobId: null };
const view = document.getElementById("view");

const ACTIONS = {
  job_created: "utworzenie oferty",
  cv_uploaded: "dodanie CV",
  evaluation_completed: "ocena CV",
  injection_flagged: "flaga: próba manipulacji",
  candidate_viewed: "podgląd kandydata",
  identity_revealed: "odkrycie danych osobowych",
  decision_recorded: "decyzja człowieka",
  explanation_generated: "wyjaśnienie dla kandydata",
  demographics_submitted: "ankieta złożona",
  demographics_withdrawn: "ankieta wycofana",
  demo_seeded: "dane przykładowe",
};
const SOURCES = { ai: "AI", offline: "offline", human: "człowiek", system: "system" };
const REC_CLASS = { invite: "badge-ok", consider: "badge-warn", mismatch: "badge-muted" };
const VERDICT_CLASS = { meets: "badge-ok", partial: "badge-warn", fails: "badge-bad", no_data: "badge-muted" };
const STATUS_CLASS = { new: "badge-outline", invited: "badge-ok", rejected: "badge-bad", on_hold: "badge-warn" };

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function actor() {
  return document.getElementById("actor").value.trim();
}
function label(group, key) {
  return (state.config && state.config.labels[group][key]) || key || "—";
}
function pct(x) {
  return x === null || x === undefined ? "—" : `${(x * 100).toFixed(1).replace(".", ",")}%`;
}
function num(x, digits = 1) {
  return x === null || x === undefined ? "—" : Number(x).toFixed(digits).replace(".", ",");
}
function fmtDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleString("pl-PL", { dateStyle: "short", timeStyle: "medium" });
}
function badge(text, cls) {
  return `<span class="badge ${cls}">${esc(text)}</span>`;
}
function toast(message, isError = false) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.className = "toast" + (isError ? " error" : "");
  el.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => (el.hidden = true), isError ? 6000 : 3500);
}

async function api(path, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  if (actor()) headers["X-Actor"] = actor();
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(options.json);
  }
  const res = await fetch(path, { ...options, headers });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.detail || `Błąd ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

// --- start ------------------------------------------------------------------------
async function init() {
  const actorInput = document.getElementById("actor");
  try {
    actorInput.value = localStorage.getItem("preselekcja.actor") || "rekruter-1";
  } catch (_) {
    actorInput.value = "rekruter-1";
  }
  actorInput.addEventListener("change", () => {
    try { localStorage.setItem("preselekcja.actor", actor()); } catch (_) { /* brak pamięci przeglądarki */ }
  });
  document.getElementById("job-select").addEventListener("change", (e) => {
    state.jobId = Number(e.target.value) || null;
    route();
  });
  [state.config, state.health] = await Promise.all([api("/api/config"), api("/api/health")]);
  const mode = document.getElementById("mode-badge");
  mode.textContent = state.health.ai_enabled ? "Tryb: AI (z weryfikacją cytatów)" : "Tryb: offline (słowa kluczowe)";
  mode.className = "badge " + (state.health.ai_enabled ? "badge-info" : "badge-muted");
  await loadJobs();
  window.addEventListener("hashchange", route);
  route();
}

async function loadJobs(selectId) {
  state.jobs = await api("/api/jobs");
  if (selectId) state.jobId = selectId;
  if (!state.jobs.find((j) => j.id === state.jobId)) state.jobId = state.jobs.length ? state.jobs[0].id : null;
  const sel = document.getElementById("job-select");
  sel.innerHTML = state.jobs.length
    ? state.jobs.map((j) => `<option value="${j.id}" ${j.id === state.jobId ? "selected" : ""}>#${j.id} ${esc(j.title)} (${j.candidates})</option>`).join("")
    : `<option value="">— brak ofert —</option>`;
}

function currentJob() {
  return state.jobs.find((j) => j.id === state.jobId) || null;
}

function route() {
  const hash = location.hash || "#/ranking";
  const [, section, id] = hash.split("/");
  document.querySelectorAll(".tabs a").forEach((a) => {
    a.classList.toggle("active", a.dataset.tab === section || (section === "kandydat" && a.dataset.tab === "ranking"));
  });
  const handlers = { ranking: renderRanking, oferta: renderJob, dodaj: renderUpload, audyt: renderAudit, raporty: renderReports };
  const run = section === "kandydat" ? () => renderCandidate(Number(id)) : handlers[section] || renderRanking;
  Promise.resolve(run()).catch((err) => {
    view.innerHTML = `<div class="card notice bad">${esc(err.message)}</div>`;
  });
}

function emptyState() {
  return `<div class="card empty">
    <h2>Brak ofert pracy</h2>
    <p class="muted">Utwórz profil stanowiska w zakładce „Oferta” albo wczytaj fikcyjne dane przykładowe (oferta + 6 CV w formatach TXT, DOCX i PDF).</p>
    <div class="actions" style="justify-content:center">
      <button class="primary" id="seed-btn">Wczytaj dane przykładowe</button>
      <a class="btn" href="#/oferta">Utwórz ofertę</a>
    </div></div>`;
}
function bindSeed() {
  const btn = document.getElementById("seed-btn");
  if (!btn) return;
  btn.addEventListener("click", async () => {
    btn.disabled = true;
    try {
      const res = await api("/api/demo/seed", { method: "POST" });
      await loadJobs(res.job_id);
      toast(`Wczytano ofertę i ${res.candidates.length} CV.`);
      location.hash = "#/ranking";
      route();
    } catch (err) {
      toast(err.message, true);
      btn.disabled = false;
    }
  });
}

// --- ranking ------------------------------------------------------------------
function scoreBar(score) {
  if (score === null || score === undefined) return "—";
  const t = state.config.thresholds;
  return `<div class="scorebar" title="Progi: zaproś ≥ ${t.invite_min}, do rozważenia ≥ ${t.consider_min}">
    <div class="bar"><span style="width:${Math.max(0, Math.min(100, score))}%"></span>
      <i style="left:${t.consider_min}%"></i><i style="left:${t.invite_min}%"></i></div>
    <b>${num(score)}</b></div>`;
}

async function renderRanking() {
  const job = currentJob();
  if (!job) { view.innerHTML = emptyState(); bindSeed(); return; }
  const rows = await api(`/api/jobs/${job.id}/candidates`);
  const t = state.config.thresholds;
  view.innerHTML = `
    <div class="card">
      <div class="card-head">
        <div><h2>${esc(job.title)}</h2><p class="muted small">Ranking w trybie blind — kandydaci widoczni tylko pod kodem. Kliknij wiersz, aby zobaczyć dowody i podjąć decyzję.</p></div>
        <a class="btn" href="#/dodaj">+ Dodaj CV</a>
      </div>
      <div class="notice">Ranking i rekomendacja to podpowiedź, nie decyzja. System nigdy sam nie odrzuca kandydatów.
        Progi: <b>zaproś</b> ≥ ${t.invite_min} pkt, <b>do rozważenia</b> ≥ ${t.consider_min} pkt, poniżej — <b>niedopasowanie</b>.</div>
      ${rows.length ? `<div class="table-wrap"><table>
        <thead><tr><th>#</th><th>Kandydat</th><th>Wynik</th><th>Rekomendacja</th><th class="num">Obowiązkowe spełnione</th><th>Flagi</th><th>Źródło oceny</th><th>Status</th></tr></thead>
        <tbody>${rows.map((r, i) => `
          <tr class="clickable" data-id="${r.candidate_id}" tabindex="0">
            <td class="num">${i + 1}</td>
            <td><b>${esc(r.code)}</b><div class="muted small">${esc(r.source_format.toUpperCase())}</div></td>
            <td>${scoreBar(r.score)}</td>
            <td>${r.recommendation ? badge(label("recommendation", r.recommendation), REC_CLASS[r.recommendation]) : "—"}</td>
            <td class="num">${r.must_met}/${r.must_total}</td>
            <td>${r.injection_flagged ? badge("próba manipulacji", "badge-bad") : ""} ${r.revealed ? badge("dane odkryte", "badge-outline") : ""}</td>
            <td>${r.source ? badge(SOURCES[r.source], r.source === "ai" ? "badge-info" : "badge-muted") : "—"}</td>
            <td>${badge(label("status", r.status), STATUS_CLASS[r.status])}</td>
          </tr>`).join("")}</tbody></table></div>`
        : `<p class="muted">Brak kandydatów dla tej oferty. <a href="#/dodaj">Dodaj pierwsze CV.</a></p>`}
    </div>`;
  view.querySelectorAll("tr.clickable").forEach((tr) => {
    const open = () => (location.hash = `#/kandydat/${tr.dataset.id}`);
    tr.addEventListener("click", open);
    tr.addEventListener("keydown", (e) => { if (e.key === "Enter") open(); });
  });
}

// --- szczegóły kandydata -------------------------------------------------------------
function highlight(text, evaluation) {
  const marks = new Array(text.length).fill(null);
  if (evaluation) {
    evaluation.results.forEach((r) => {
      if (r.evidence_start === null || r.evidence_end === null) return;
      const mark = { cls: "ev", title: `Dowód: ${r.name}` };
      for (let i = r.evidence_start; i < r.evidence_end && i < text.length; i++) {
        if (!marks[i]) marks[i] = mark;
      }
    });
    const omitted = { cls: "inj", title: "Fragment pominięty w ocenie (podejrzenie manipulacji)" };
    (evaluation.injection.omitted || []).forEach((sp) => {
      for (let i = sp.start; i < sp.end && i < text.length; i++) marks[i] = omitted;
    });
    evaluation.injection.findings.forEach((f) => {
      const mark = { cls: "inj", title: `Podejrzenie manipulacji: ${f.label}` };
      for (let i = f.start; i < f.end && i < text.length; i++) marks[i] = mark;
    });
  }
  let out = "";
  let i = 0;
  while (i < text.length) {
    const m = marks[i];
    let j = i;
    while (j < text.length && marks[j] === m) j++;
    const chunk = esc(text.slice(i, j)).replace(/\[[A-ZĄĆĘŁŃÓŚŹŻ :\-\/,.a-ząćęłńóśźż]+\]/g, (p) => `<span class="ph">${p}</span>`);
    out += m ? `<mark class="${m.cls}" title="${esc(m.title)}">${chunk}</mark>` : chunk;
    i = j;
  }
  return out;
}

function requirementItem(r) {
  const degraded = r.model_verdict && r.model_verdict !== r.verdict;
  return `<li>
    <div class="req-head">
      <span class="req-name">${esc(r.name)}</span>
      <span>${badge(label("kind", r.kind), "badge-outline")} ${badge("waga " + r.weight, "badge-outline")} ${badge(label("verdict", r.verdict), VERDICT_CLASS[r.verdict])}</span>
    </div>
    ${r.evidence ? `<blockquote class="evidence">„${esc(r.evidence)}”</blockquote>` : ""}
    ${r.rationale ? `<p class="req-note">Uzasadnienie modelu: ${esc(r.rationale)}</p>` : ""}
    ${r.matched_terms && r.matched_terms.length ? `<p class="req-note">Dopasowane terminy: ${r.matched_terms.map(esc).join(", ")}</p>` : ""}
    ${r.note ? `<p class="req-note ${degraded ? "warn" : ""}">${degraded ? `Model: „${esc(label("verdict", r.model_verdict))}”. ` : ""}${esc(r.note)}</p>` : ""}
    <p class="req-note">Punkty: ${num(r.points, 1)} / ${r.weight}</p>
  </li>`;
}

async function renderCandidate(id) {
  const c = await api(`/api/candidates/${id}`);
  if (c.job_id !== state.jobId) { state.jobId = c.job_id; await loadJobs(c.job_id); }
  const ev = c.evaluation;
  const t = state.config.thresholds;
  const masked = Object.entries(c.anonymization.counts || {}).map(([k, n]) => `${esc(label("masked", k))} (${n})`).join(", ");
  const injection = ev && ev.injection.flagged ? `
    <div class="notice bad"><b>Wykryto możliwą próbę manipulacji oceną (prompt injection).</b>
      Zaznaczone fragmenty pominięto w ocenie. Sprawdź CV ręcznie.
      <ul>${ev.injection.findings.map((f) => `<li>${esc(f.label)}${f.fragment ? `: „${esc(f.fragment)}”` : ""}</li>`).join("")}
      ${ev.injection.ai_suspected ? `<li>Model AI również zgłosił próbę: ${esc(ev.injection.ai_note)}</li>` : ""}</ul></div>` : "";
  const lastDecision = c.decisions.length ? c.decisions[c.decisions.length - 1] : null;

  view.innerHTML = `
    <p><a href="#/ranking">← Wróć do rankingu</a></p>
    <div class="card">
      <div class="detail-head">
        <div>
          <h2>Kandydat ${esc(c.code)}</h2>
          <div class="meta">
            ${badge(label("status", c.status), STATUS_CLASS[c.status])}
            ${ev ? badge("Rekomendacja: " + label("recommendation", ev.recommendation), REC_CLASS[ev.recommendation]) : ""}
            ${ev ? badge("Źródło: " + SOURCES[ev.source], ev.source === "ai" ? "badge-info" : "badge-muted") : ""}
            ${c.revealed ? badge("dane osobowe odkryte", "badge-outline") : badge("tryb blind", "badge-outline")}
          </div>
        </div>
        ${ev ? `<div style="text-align:right"><div class="score-big">${num(ev.score)}</div><div class="muted small">wynik / 100</div></div>` : ""}
      </div>
      ${ev ? `<p class="formula">Wynik = Σ(waga × punkty za werdykt) / Σ wag × 100 = ${num(ev.weighted_points, 1)} / ${num(ev.max_points, 0)} × 100.
        Punkty: spełnia 1, częściowo 0,5, nie spełnia 0, brak danych 0. Progi: zaproś ≥ ${t.invite_min}, do rozważenia ≥ ${t.consider_min}.
        Wynik liczy kod, nie model.</p>` : ""}
      ${ev && ev.gates.length ? `<div class="notice warn">${ev.gates.map(esc).join("<br>")}</div>` : ""}
      ${ev && ev.fallback_reason ? `<div class="notice warn">${esc(ev.fallback_reason)}</div>` : ""}
      ${injection}
    </div>

    <div class="grid grid-detail">
      <div>
        <div class="card">
          <div class="card-head"><h3>Ocena per wymaganie</h3><span class="muted small">${c.evaluations_count} ocen(y) w historii</span></div>
          <ul class="req-list">${ev ? ev.results.map(requirementItem).join("") : "<li>Brak oceny.</li>"}</ul>
          <div class="actions">
            <button data-eval="offline">Oceń ponownie (offline)</button>
            ${state.health.ai_enabled ? `<button data-eval="auto">Oceń ponownie (AI)</button>` : ""}
          </div>
        </div>
        <div class="card">
          <div class="card-head"><h3>CV (wersja zanonimizowana)</h3>
            <button class="danger" id="reveal-toggle">Odkryj dane osobowe…</button></div>
          <div class="legend"><span><mark class="ev">dowód</mark> fragment użyty jako dowód</span><span><mark class="inj">manipulacja</mark> pominięte w ocenie</span></div>
          <p class="muted small">Zamaskowano: ${masked || "nic"}. Zneutralizowane formy rodzajowe: ${c.anonymization.gender_neutralized || 0}.</p>
          <div id="reveal-form" hidden class="notice warn">
            <label class="block"><span>Uzasadnienie odkrycia danych <span class="hint">(trafi do dziennika audytowego)</span></span>
              <input type="text" id="reveal-reason" placeholder="np. kontakt w sprawie zaproszenia na rozmowę"></label>
            <div class="actions"><button class="danger" id="reveal-confirm">Odkryj i zapisz w dzienniku</button><button id="reveal-cancel">Anuluj</button></div>
          </div>
          <div id="revealed"></div>
          <div class="cv-text">${highlight(c.anonymized_text, ev)}</div>
        </div>
      </div>

      <div>
        <div class="card">
          <h3>Decyzja człowieka</h3>
          <p class="muted small">Rekomendacja systemu (${ev ? esc(SOURCES[ev.source]) : "—"}): <b>${ev ? esc(label("recommendation", ev.recommendation)) : "—"}</b>. Decyzja należy do Ciebie i jest zapisywana w dzienniku.
            Odrzucenie wymaga uzasadnienia (min. ${state.config.min_rejection_reason} znaków).</p>
          <form id="decision-form">
            <div class="radio-row">
              ${["invited", "on_hold", "rejected"].map((s) => `<label><input type="radio" name="status" value="${s}" required><span>${esc(label("status", s))}</span></label>`).join("")}
            </div>
            <div id="override-hint" class="notice warn" hidden>Ta decyzja jest niezgodna z rekomendacją systemu — zostanie odnotowana jako „override”. To w porządku, jeśli masz uzasadnione powody; metryka służy wykrywaniu bezrefleksyjnego zatwierdzania.</div>
            <label class="block"><span>Uzasadnienie <span class="hint" id="reason-hint">(opcjonalne)</span></span>
              <textarea name="reason" maxlength="2000" placeholder="Na czym opierasz decyzję?"></textarea></label>
            <button class="primary" type="submit">Zapisz decyzję</button>
          </form>
          ${c.decisions.length ? `<h3 style="margin-top:1rem">Historia decyzji</h3>
            <table><thead><tr><th>Kiedy</th><th>Kto</th><th>Decyzja</th><th>Rekomendacja</th></tr></thead><tbody>
            ${c.decisions.slice().reverse().map((d) => `<tr><td class="small">${fmtDate(d.created_at)}</td><td>${esc(d.actor)}</td>
              <td>${badge(label("status", d.status), STATUS_CLASS[d.status])} ${d.is_override ? badge("override", "badge-warn") : ""}
              ${d.reason ? `<div class="small muted">${esc(d.reason)}</div>` : ""}</td>
              <td class="small">${esc(label("recommendation", d.ai_recommendation))}</td></tr>`).join("")}
            </tbody></table>` : ""}
        </div>
        <div class="card">
          <h3>Wyjaśnienie dla kandydata</h3>
          <p class="muted small">Informacja o użyciu AI i czynnikach oceny (art. 26 i 86 AI Act). Szkic do przejrzenia przed wysłaniem.</p>
          <div class="actions">
            <button id="exp-offline">Generuj (szablon)</button>
            ${state.health.ai_enabled ? `<button id="exp-ai">Generuj (wersja AI)</button>` : ""}
          </div>
          <div id="explanation"></div>
        </div>
      </div>
    </div>`;

  // ponowna ocena
  view.querySelectorAll("[data-eval]").forEach((btn) => btn.addEventListener("click", async () => {
    btn.disabled = true;
    try {
      await api(`/api/candidates/${id}/evaluate?mode=${btn.dataset.eval}`, { method: "POST" });
      toast("Ocena zaktualizowana.");
      renderCandidate(id);
    } catch (err) { toast(err.message, true); btn.disabled = false; }
  }));

  // odkrycie danych
  const form = document.getElementById("reveal-form");
  document.getElementById("reveal-toggle").addEventListener("click", () => { form.hidden = false; document.getElementById("reveal-reason").focus(); });
  document.getElementById("reveal-cancel").addEventListener("click", () => (form.hidden = true));
  document.getElementById("reveal-confirm").addEventListener("click", async () => {
    try {
      const res = await api(`/api/candidates/${id}/reveal`, { method: "POST", json: { reason: document.getElementById("reveal-reason").value } });
      form.hidden = true;
      const ident = Object.entries(res.identity).map(([k, v]) => `<div class="kv"><b>${esc(label("masked", k))}:</b> ${v.map(esc).join("; ")}</div>`).join("");
      document.getElementById("revealed").innerHTML = `<div class="notice bad">${esc(res.notice)}</div>${ident}
        <h3 style="margin-top:.75rem">Oryginalna treść</h3><div class="cv-text revealed">${esc(res.original_text)}</div><h3 style="margin-top:.75rem">Wersja zanonimizowana</h3>`;
      toast("Dane odkryte — zapisano w dzienniku.");
    } catch (err) { toast(err.message, true); }
  });

  // decyzja
  const dForm = document.getElementById("decision-form");
  const rec = ev ? ev.recommendation : null;
  dForm.addEventListener("change", () => {
    const status = (dForm.querySelector("input[name=status]:checked") || {}).value;
    const override = (rec === "invite" && status === "rejected") || (rec === "mismatch" && status === "invited");
    document.getElementById("override-hint").hidden = !override;
    document.getElementById("reason-hint").textContent = status === "rejected" ? `(wymagane, min. ${state.config.min_rejection_reason} znaków)` : "(opcjonalne)";
  });
  dForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = (dForm.querySelector("input[name=status]:checked") || {}).value;
    const reason = dForm.reason.value.trim();
    if (!actor()) { toast("Podaj identyfikator użytkownika w nagłówku strony.", true); return; }
    if (status === "rejected" && reason.length < state.config.min_rejection_reason) {
      toast(`Odrzucenie wymaga uzasadnienia (min. ${state.config.min_rejection_reason} znaków).`, true);
      dForm.reason.focus();
      return;
    }
    try {
      await api(`/api/candidates/${id}/decisions`, { method: "POST", json: { status, reason } });
      toast("Decyzja zapisana w dzienniku.");
      renderCandidate(id);
    } catch (err) { toast(err.message, true); }
  });

  // wyjaśnienie
  const showExp = async (ai) => {
    try {
      const exp = await api(`/api/candidates/${id}/explanation?ai=${ai}`);
      document.getElementById("explanation").innerHTML = `
        <p class="small" style="margin-top:.75rem">${badge("Źródło: " + SOURCES[exp.source], exp.source === "ai" ? "badge-info" : "badge-muted")}
        ${exp.fallback_reason ? `<span class="muted"> ${esc(exp.fallback_reason)}</span>` : ""}</p>
        <label class="block"><span>Temat</span><input type="text" readonly value="${esc(exp.subject)}"></label>
        <textarea readonly rows="16" id="exp-body">${esc(exp.body)}</textarea>
        <div class="actions" style="margin-top:.5rem"><button id="exp-copy">Kopiuj treść</button></div>`;
      document.getElementById("exp-copy").addEventListener("click", async () => {
        try { await navigator.clipboard.writeText(exp.body); toast("Skopiowano."); } catch (_) { document.getElementById("exp-body").select(); }
      });
    } catch (err) { toast(err.message, true); }
  };
  document.getElementById("exp-offline").addEventListener("click", () => showExp(false));
  const expAi = document.getElementById("exp-ai");
  if (expAi) expAi.addEventListener("click", () => showExp(true));
  if (lastDecision) dForm.querySelector(`input[value=${lastDecision.status}]`).checked = true;
  dForm.dispatchEvent(new Event("change"));
}

// --- oferta ---------------------------------------------------------------------
function reqRow(r = {}) {
  return `<div class="req-row">
    <input type="text" name="name" placeholder="np. Znajomość prawa pracy" value="${esc(r.name || "")}" aria-label="Nazwa wymagania">
    <select name="kind" aria-label="Typ"><option value="must">obowiązkowe</option><option value="nice" ${r.kind === "nice" ? "selected" : ""}>mile widziane</option></select>
    <input type="number" name="weight" min="1" max="5" value="${r.weight || 3}" aria-label="Waga 1–5">
    <input type="text" name="keywords" placeholder="prawo pracy, Kodeks pracy" value="${esc((r.keywords || []).join(", "))}" aria-label="Słowa kluczowe">
    <input type="text" name="synonyms" placeholder="przepisy kadrowe" value="${esc((r.synonyms || []).join(", "))}" aria-label="Synonimy">
    <input type="text" name="partial_keywords" placeholder="np. angielski B1" value="${esc((r.partial_keywords || []).join(", "))}" aria-label="Częściowo, gdy">
    <input type="number" name="min_years" min="0" max="50" step="0.5" value="${r.min_years ?? ""}" placeholder="—" aria-label="Min. lat">
    <button type="button" class="danger" data-remove title="Usuń wymaganie">✕</button>
  </div>`;
}
const splitTerms = (s) => s.split(",").map((x) => x.trim()).filter(Boolean);

async function renderJob() {
  const job = currentJob();
  const t = state.config.thresholds;
  view.innerHTML = `
    ${job ? `<div class="card">
      <div class="card-head"><h2>${esc(job.title)}</h2><span class="muted small">utworzona ${fmtDate(job.created_at)} przez ${esc(job.created_by)}</span></div>
      ${job.description ? `<p>${esc(job.description)}</p>` : ""}
      <div class="notice small">Profil jest zamrożony po utworzeniu — zmiana kryteriów w trakcie naboru byłaby nierówna wobec kandydatów ocenionych wcześniej. Aby zmienić kryteria, utwórz nową wersję oferty.</div>
      <div class="table-wrap"><table>
        <thead><tr><th>Wymaganie</th><th>Typ</th><th class="num">Waga</th><th>Słowa kluczowe</th><th>Synonimy</th><th>Częściowo, gdy</th><th class="num">Min. lat</th></tr></thead>
        <tbody>${job.requirements.map((r) => `<tr><td><b>${esc(r.name)}</b><div class="muted small mono">${esc(r.id)}</div></td>
          <td>${badge(label("kind", r.kind), r.kind === "must" ? "badge-info" : "badge-outline")}</td><td class="num">${r.weight}</td>
          <td class="small">${r.keywords.map(esc).join(", ")}</td><td class="small">${r.synonyms.map(esc).join(", ") || "—"}</td>
          <td class="small">${(r.partial_keywords || []).map(esc).join(", ") || "—"}</td><td class="num">${r.min_years ?? "—"}</td></tr>`).join("")}</tbody>
      </table></div>
    </div>` : ""}
    <div class="card">
      <h2>Jak liczony jest wynik</h2>
      <p class="small">Każde wymaganie dostaje werdykt: <b>spełnia</b> (1 pkt × waga), <b>częściowo</b> (0,5), <b>nie spełnia</b> (0), <b>brak danych</b> (0).
      Wynik = suma punktów / suma wag × 100. Rekomendacja: <b>zaproś</b> ≥ ${t.invite_min}, <b>do rozważenia</b> ≥ ${t.consider_min}, poniżej <b>niedopasowanie</b>.
      Bramki: wymaganie obowiązkowe „nie spełnia” → najwyżej „niedopasowanie”; obowiązkowe „brak danych” → najwyżej „do rozważenia”.
      W trybie AI model zwraca tylko werdykty i cytaty — cytat musi występować w CV, inaczej werdykt spada do „brak danych”. Liczby liczy kod.</p>
    </div>
    <div class="card">
      <h2>Nowa oferta (profil stanowiska)</h2>
      <form id="job-form">
        <label class="block"><span>Tytuł stanowiska</span><input type="text" name="title" required minlength="3" placeholder="np. Specjalista / Specjalistka ds. kadr i płac"></label>
        <label class="block"><span>Opis <span class="hint">(opcjonalny)</span></span><textarea name="description" rows="2"></textarea></label>
        <h3>Wymagania</h3>
        <p class="hint">Słowa kluczowe i synonimy rozdzielaj przecinkami — służą ocenie offline (z prostą obsługą odmiany). „Częściowo, gdy” — terminy oznaczające częściowe spełnienie. „Min. lat” — wymagany staż.</p>
        <div class="req-row req-row-head"><span>Nazwa</span><span>Typ</span><span>Waga</span><span>Słowa kluczowe</span><span>Synonimy</span><span>Częściowo, gdy</span><span>Min. lat</span><span></span></div>
        <div id="req-rows">${reqRow({ kind: "must", weight: 4 })}${reqRow({ kind: "nice", weight: 2 })}</div>
        <div class="actions"><button type="button" id="add-req">+ Dodaj wymaganie</button><button class="primary" type="submit">Utwórz ofertę</button></div>
      </form>
    </div>`;
  const rowsEl = document.getElementById("req-rows");
  document.getElementById("add-req").addEventListener("click", () => rowsEl.insertAdjacentHTML("beforeend", reqRow()));
  rowsEl.addEventListener("click", (e) => { if (e.target.matches("[data-remove]")) e.target.closest(".req-row").remove(); });
  document.getElementById("job-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const f = e.target;
    const requirements = [...rowsEl.querySelectorAll(".req-row")].map((row) => {
      const v = (n) => row.querySelector(`[name=${n}]`).value;
      return {
        name: v("name").trim(), kind: v("kind"), weight: Number(v("weight")),
        keywords: splitTerms(v("keywords")), synonyms: splitTerms(v("synonyms")),
        partial_keywords: splitTerms(v("partial_keywords")), min_years: v("min_years") === "" ? null : Number(v("min_years")),
      };
    });
    try {
      const job = await api("/api/jobs", { method: "POST", json: { title: f.title.value.trim(), description: f.description.value.trim(), requirements } });
      await loadJobs(job.id);
      toast("Oferta utworzona.");
      location.hash = "#/dodaj";
    } catch (err) { toast(err.message, true); }
  });
}

// --- dodawanie CV i ankieta --------------------------------------------------------------
async function renderUpload() {
  const job = currentJob();
  if (!job) { view.innerHTML = emptyState(); bindSeed(); return; }
  const rows = await api(`/api/jobs/${job.id}/candidates`);
  const L = state.config.labels.demographics;
  const opts = (dim) => Object.entries(L[dim]).map(([k, v]) => `<option value="${k}" ${k === "undisclosed" ? "selected" : ""}>${esc(v)}</option>`).join("");
  view.innerHTML = `
    <div class="grid grid-2">
      <div class="card">
        <h2>Dodaj CV do: ${esc(job.title)}</h2>
        <p class="muted small">Obsługiwane: wklejony tekst, .txt, .pdf, .docx (maks. ${state.config.max_upload_mb} MB). Przed oceną dane identyfikujące są maskowane; nazwa pliku nie jest pokazywana ani zapisywana w dzienniku.</p>
        <form id="upload-form">
          <label class="block"><span>Plik CV</span><input type="file" name="file" accept=".txt,.pdf,.docx"></label>
          <label class="block"><span>…albo wklej tekst CV</span><textarea name="text" rows="8" placeholder="Treść CV"></textarea></label>
          <label class="block"><span>Tryb oceny</span>
            <select name="mode"><option value="auto">automatyczny (AI, jeśli dostępne; inaczej offline)</option><option value="offline">tylko offline</option></select></label>
          <button class="primary" type="submit">Dodaj i oceń</button>
        </form>
      </div>
      <div class="card">
        <h2>Dobrowolna ankieta różnorodności</h2>
        <div class="notice small">Symulacja formularza wypełnianego przez <b>kandydata</b>. Odpowiedzi są dobrowolne, zapisywane w osobnej bazie,
          nigdy nie są pokazywane oceniającym ani wysyłane do modelu — służą wyłącznie do zbiorczego raportu stronniczości. Po zapisie nie da się ich tu odczytać.</div>
        <form id="demo-form">
          <label class="block"><span>Kandydat</span><select name="cid" required>${rows.map((r) => `<option value="${r.candidate_id}">${esc(r.code)}</option>`).join("")}</select></label>
          <label class="block"><span>Płeć</span><select name="gender">${opts("gender")}</select></label>
          <label class="block"><span>Wiek</span><select name="age_group">${opts("age_group")}</select></label>
          <label class="block"><span>Niepełnosprawność</span><select name="disability">${opts("disability")}</select></label>
          <label class="block" style="font-weight:500"><input type="checkbox" name="consent"> Kandydat wyraża zgodę na wykorzystanie odpowiedzi w zbiorczym raporcie</label>
          <div class="actions"><button class="primary" type="submit" ${rows.length ? "" : "disabled"}>Zapisz ankietę</button>
            <button type="button" id="demo-withdraw" ${rows.length ? "" : "disabled"}>Wycofaj zgodę i usuń</button></div>
        </form>
      </div>
    </div>`;
  document.getElementById("upload-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const f = e.target;
    const fd = new FormData();
    if (f.file.files[0]) fd.append("file", f.file.files[0]);
    if (f.text.value.trim()) fd.append("text", f.text.value);
    fd.append("mode", f.mode.value);
    const btn = f.querySelector("button[type=submit]");
    btn.disabled = true;
    try {
      const c = await api(`/api/jobs/${job.id}/candidates`, { method: "POST", body: fd });
      await loadJobs(job.id);
      toast(`Dodano ${c.code}.`);
      location.hash = `#/kandydat/${c.candidate_id}`;
    } catch (err) { toast(err.message, true); btn.disabled = false; }
  });
  const dForm = document.getElementById("demo-form");
  dForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await api(`/api/candidates/${dForm.cid.value}/demographics`, { method: "PUT", json: {
        consent: dForm.consent.checked, gender: dForm.gender.value, age_group: dForm.age_group.value, disability: dForm.disability.value } });
      dForm.reset();
      toast("Ankieta zapisana (osobno, poza oceną).");
    } catch (err) { toast(err.message, true); }
  });
  document.getElementById("demo-withdraw").addEventListener("click", async () => {
    try {
      await api(`/api/candidates/${dForm.cid.value}/demographics`, { method: "DELETE" });
      toast("Zgoda wycofana — odpowiedzi usunięte.");
    } catch (err) { toast(err.message, true); }
  });
}

// --- dziennik audytowy ---------------------------------------------------------------
const DETAIL_LABELS = {
  ai_recommendation: "rekomendacja systemu", decision_id: "nr decyzji", override: "niezgodna z rekomendacją",
  status: "decyzja", reason: "uzasadnienie", viewed_before: "dowody otwarte przed decyzją", score: "wynik",
  recommendation: "rekomendacja", verdicts: "werdykty", degraded_quotes: "odrzucone cytaty AI",
  injection_flagged: "flaga manipulacji", fallback_reason: "powód trybu offline", evaluation_id: "nr oceny",
  llm: "model", bytes: "rozmiar (B)", format: "format", job_id: "oferta", masked: "zamaskowano",
  gender_neutralized: "zneutralizowane formy rodzajowe", title: "tytuł", requirements: "liczba wymagań",
  candidates: "kandydaci", rules: "reguły", ai_suspected: "zgłoszone przez AI", removed: "usunięto",
};
function detailValue(k, v) {
  if (v === true) return "tak";
  if (v === false) return "nie";
  if ((k === "recommendation" || k === "ai_recommendation") && typeof v === "string") return label("recommendation", v);
  if (k === "status" && typeof v === "string") return label("status", v);
  if (k === "verdicts" && typeof v === "object") return Object.entries(v).map(([r, x]) => `${r}: ${label("verdict", x)}`).join(", ");
  if (k === "masked" && typeof v === "object") return Object.entries(v).map(([c, n]) => `${label("masked", c)} (${n})`).join(", ");
  if (Array.isArray(v)) return v.join(", ");
  return typeof v === "object" ? JSON.stringify(v) : String(v);
}
function details(d) {
  const entries = Object.entries(d || {}).filter(([, v]) => v !== null && v !== undefined && v !== "" && !(typeof v === "object" && !Object.keys(v).length));
  return entries.map(([k, v]) => `<span class="kv"><b>${esc(DETAIL_LABELS[k] || k)}:</b> ${esc(detailValue(k, v))}</span>`).join("<br>");
}

async function renderAudit() {
  const action = renderAudit.filter || "";
  const entries = await api(`/api/audit?limit=300${action ? `&action=${action}` : ""}`);
  view.innerHTML = `
    <div class="card">
      <div class="card-head">
        <div><h2>Dziennik audytowy</h2><p class="muted small">Append-only (art. 12 AI Act): wpisów nie można zmienić ani usunąć, każdy zawiera skrót poprzedniego (łańcuch SHA-256). Bez treści CV i bez danych osobowych.</p></div>
        <div class="actions">
          <select id="audit-filter" aria-label="Filtr akcji"><option value="">wszystkie akcje</option>
            ${Object.entries(ACTIONS).map(([k, v]) => `<option value="${k}" ${k === action ? "selected" : ""}>${esc(v)}</option>`).join("")}</select>
          <button id="audit-verify">Sprawdź integralność</button>
        </div>
      </div>
      <div id="verify-result"></div>
      <div class="table-wrap"><table>
        <thead><tr><th class="num">#</th><th>Kiedy</th><th>Kto</th><th>Akcja</th><th>Obiekt</th><th>Źródło</th><th>Hash wejścia</th><th>Szczegóły</th></tr></thead>
        <tbody>${entries.map((e) => `<tr>
          <td class="num">${e.id}</td><td class="small">${fmtDate(e.ts)}</td><td class="nowrap">${esc(e.actor)}</td>
          <td>${esc(ACTIONS[e.action] || e.action)}</td>
          <td class="nowrap">${e.entity_type === "candidate" ? `<a href="#/kandydat/${e.entity_id}">K-${String(e.entity_id).padStart(4, "0")}</a>` : e.entity_type === "job" ? `oferta #${e.entity_id}` : "—"}</td>
          <td>${badge(SOURCES[e.source] || e.source, e.source === "ai" ? "badge-info" : e.source === "human" ? "badge-outline" : "badge-muted")}</td>
          <td class="hash" title="${esc(e.input_hash || "")}">${e.input_hash ? esc(e.input_hash.slice(0, 12)) + "…" : "—"}</td>
          <td class="details">${details(e.details)}</td></tr>`).join("") || `<tr><td colspan="8" class="muted">Brak wpisów.</td></tr>`}</tbody>
      </table></div>
    </div>`;
  document.getElementById("audit-filter").addEventListener("change", (e) => { renderAudit.filter = e.target.value; renderAudit(); });
  document.getElementById("audit-verify").addEventListener("click", async () => {
    const r = await api("/api/audit/verify");
    document.getElementById("verify-result").innerHTML = `<div class="notice ${r.ok ? "ok" : "bad"}">${esc(r.message)}</div>`;
  });
}

// --- raporty ---------------------------------------------------------------------
async function renderReports() {
  const job = currentJob();
  const q = job ? `?job_id=${job.id}` : "";
  const [o, b] = await Promise.all([api(`/api/reports/oversight${q}`), api(`/api/reports/bias${q}`)]);
  const stages = state.config.labels.stages;
  const stageKeys = Object.keys(stages);
  view.innerHTML = `
    <div class="card">
      <h2>Nadzór człowieka ${job ? `— ${esc(job.title)}` : ""}</h2>
      <p class="muted small">Override rate = odsetek decyzji końcowych przeciwnych do jednoznacznej rekomendacji AI („zaproś” → odrzucony, „niedopasowanie” → zaproszony).
        Bardzo niski (&lt; ${pct(state.config.override_bounds.low)}) może oznaczać automation bias, bardzo wysoki (&gt; ${pct(state.config.override_bounds.high)}) — źle skalibrowany profil.</p>
      <div class="kpis">
        <div class="kpi"><div class="v">${o.final_decisions}</div><div class="l">decyzje końcowe</div></div>
        <div class="kpi"><div class="v">${pct(o.override_rate)}</div><div class="l">override rate (${o.overrides}/${o.directional_decisions})</div></div>
        <div class="kpi"><div class="v">${pct(o.agreement_rate)}</div><div class="l">zgodność z AI</div></div>
        <div class="kpi"><div class="v">${o.decisions_without_review}</div><div class="l">decyzje bez otwarcia dowodów</div></div>
        <div class="kpi"><div class="v">${o.on_hold}</div><div class="l">wstrzymane</div></div>
      </div>
      ${o.interpretation.map((n) => `<div class="notice small">${esc(n)}</div>`).join("")}
    </div>
    <div class="card">
      <h2>Monitoring stronniczości (reguła 4/5)</h2>
      <p class="muted small">Wskaźnik selekcji = wybrani / wszyscy w grupie. Impact ratio = wskaźnik grupy / najwyższy wskaźnik.
        Wartość &lt; ${num(state.config.four_fifths, 1)} to sygnał możliwego niekorzystnego wpływu (heurystyka, nie dowód dyskryminacji).
        Źródło: dobrowolna ankieta (${b.respondents} z ${b.candidates_total} kandydatów). Grupa „nie podano” nie uczestniczy w porównaniu.</p>
      ${b.warnings.map((w) => `<div class="notice warn small">${esc(w)}</div>`).join("")}
      ${b.dimensions.map((d) => {
        const groups = [...new Set(d.stages.flatMap((s) => s.groups.map((g) => g.group)))];
        if (!groups.length) return "";
        return `<h3 style="margin-top:1rem">${esc(d.label)}</h3><div class="table-wrap"><table>
          <thead><tr><th>Grupa</th><th class="num">n</th>${stageKeys.map((k) => `<th>${esc(stages[k])}</th>`).join("")}</tr></thead>
          <tbody>${groups.map((g) => {
            const first = d.stages[0].groups.find((x) => x.group === g);
            return `<tr><td>${esc(first ? first.label : g)}${first && first.small_sample ? ` <span class="badge badge-muted" title="Mniej niż ${state.config.small_sample} osób">mała próba</span>` : ""}</td>
              <td class="num">${first ? first.total : 0}</td>
              ${d.stages.map((s) => {
                const x = s.groups.find((y) => y.group === g);
                if (!x) return "<td>—</td>";
                return `<td class="${x.flagged ? "cell-flag" : ""}">${x.selected}/${x.total} = ${pct(x.rate)}
                  ${x.excluded_from_ratio ? `<div class="small muted">poza porównaniem</div>` : `<div class="small">IR: ${x.impact_ratio === null ? "—" : num(x.impact_ratio, 2)}${x.flagged ? " &lt; 4/5" : ""}</div>`}</td>`;
              }).join("")}</tr>`;
          }).join("")}</tbody></table></div>`;
      }).join("")}
    </div>`;
}

init().catch((err) => {
  view.innerHTML = `<div class="card notice bad">Nie udało się uruchomić aplikacji: ${esc(err.message)}</div>`;
});

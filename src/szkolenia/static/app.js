"use strict";

/* Prosty klient SPA: routing po #hash, dane z /api, bez zależności zewnętrznych. */

const app = document.getElementById("app");
const state = { health: null, currentEmployee: null, questionFilter: "draft", outboxFilter: "" };

const STATUS = {
  valid: "aktualne", expiring: "wygasa ≤ 30 dni", overdue: "przeterminowane",
  incomplete: "nieukończone", not_applicable: "nie dotyczy",
};
const KIND = { reminder: "przypomnienie", overdue: "po terminie", escalation: "eskalacja" };
const ORIGIN = { ai: "AI", offline: "offline", manual: "ręczne" };
const Q_STATUS = { draft: "szkic", approved: "zatwierdzone", rejected: "odrzucone" };

// --- Narzędzia ---------------------------------------------------------------------------

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function fmtDate(iso) {
  if (!iso) return "—";
  const [y, m, d] = iso.slice(0, 10).split("-");
  return `${d}.${m}.${y}`;
}

function fmtDateTime(iso) {
  if (!iso) return "—";
  return `${fmtDate(iso)} ${iso.slice(11, 16)}`;
}

function plural(n, one, few, many) {
  const a = Math.abs(n);
  if (a === 1) return one;
  if (a % 10 >= 2 && a % 10 <= 4 && (a % 100 < 12 || a % 100 > 14)) return few;
  return many;
}

function chip(cls, text) {
  return `<span class="chip ${esc(cls)}">${esc(text)}</span>`;
}

function statusChip(status) {
  return chip(status, STATUS[status] || status);
}

function pctBar(pct) {
  const cls = pct >= 90 ? "good" : pct >= 70 ? "mid" : "low";
  return `<div class="bar ${cls}" role="img" aria-label="${pct}%"><span style="width:${Math.max(0, Math.min(100, pct))}%"></span></div>`;
}

function toast(message, isError = false) {
  const el = document.createElement("div");
  el.className = "toast" + (isError ? " error" : "");
  el.textContent = message;
  const box = document.getElementById("toasts");
  box.appendChild(el);
  while (box.children.length > 3) box.firstElementChild.remove();
  setTimeout(() => el.remove(), isError ? 7000 : 4500);
}

async function api(path, options = {}) {
  const opts = { headers: {}, ...options };
  if (opts.body && typeof opts.body !== "string") {
    opts.body = JSON.stringify(opts.body);
    opts.headers["Content-Type"] = "application/json";
  }
  const res = await fetch(path, opts);
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    let msg = data.detail || `Błąd ${res.status}`;
    if (Array.isArray(data.errors) && data.errors.length) {
      msg += " " + data.errors.map((e) => (e.field ? `${e.field}: ` : "") + e.message).join("; ");
    }
    throw new Error(msg);
  }
  return data;
}

function guard(fn) {
  return async (...args) => {
    try {
      await fn(...args);
    } catch (err) {
      toast(err.message, true);
    }
  };
}

function formData(form) {
  const out = {};
  new FormData(form).forEach((v, k) => { out[k] = typeof v === "string" ? v.trim() : v; });
  return out;
}

// --- Routing -----------------------------------------------------------------------------

const routes = {
  pulpit: renderDashboard,
  pracownicy: renderEmployees,
  szkolenia: renderTrainings,
  moje: renderMyTrainings,
  test: renderQuiz,
  raport: renderReport,
  przypomnienia: renderOutbox,
  weryfikacja: renderVerify,
};

async function route() {
  const [view, ...params] = (location.hash.replace(/^#/, "") || "pulpit").split("/");
  const handler = routes[view] || renderDashboard;
  const tab = view === "test" ? "moje" : view;
  document.querySelectorAll(".tabs a").forEach((a) => a.classList.toggle("active", a.dataset.view === tab));
  window.scrollTo(0, 0);
  try {
    await handler(...params);
  } catch (err) {
    app.innerHTML = `<div class="notice bad">${esc(err.message)}</div>`;
  }
}

window.addEventListener("hashchange", route);

async function init() {
  try {
    state.health = await api("/api/health");
    document.getElementById("company").textContent = state.health.company;
    document.getElementById("today-label").textContent = `Dziś: ${fmtDate(state.health.today)}`;
    const badge = document.getElementById("ai-badge");
    const ai = state.health.ai_mode === "ai";
    badge.textContent = ai ? "Generator pytań: AI (Claude)" : "Generator pytań: offline";
    badge.classList.toggle("ai", ai);
  } catch (err) {
    toast("Nie udało się połączyć z serwerem: " + err.message, true);
  }
  route();
}

async function departmentOptions(selected) {
  const deps = await api("/api/departments");
  return `<option value="">Wszystkie działy</option>` +
    deps.map((d) => `<option ${d === selected ? "selected" : ""}>${esc(d)}</option>`).join("");
}

// --- Pulpit ------------------------------------------------------------------------------

async function renderDashboard() {
  const dep = sessionStorage.getItem("dep") || "";
  const [d, depOpts] = await Promise.all([
    api("/api/dashboard" + (dep ? `?department=${encodeURIComponent(dep)}` : "")),
    departmentOptions(dep),
  ]);
  const bars = (list) => list.length ? `<div class="bars">${list.map((b) => `
      <div class="item"><span class="name" title="${esc(b.label)}">${esc(b.label)}</span>
      <span class="pct">${b.percent}%</span>${pctBar(b.percent)}</div>`).join("")}</div>` : `<p class="empty">Brak danych.</p>`;

  app.innerHTML = `
    <div class="card-head">
      <div><h1>Pulpit zgodności</h1><div class="muted">Stan na ${fmtDate(d.today)} · wymaganych szkoleń: ${d.total_required}</div></div>
      <div class="row">
        <select id="dep" aria-label="Filtr działu" style="width:auto">${depOpts}</select>
        <button id="run-daily">Uruchom przegląd dzienny</button>
      </div>
    </div>
    <div class="grid grid-kpi">
      <div class="kpi main"><div class="label">Zgodność</div><div class="value">${d.compliance_percent}%</div>${pctBar(d.compliance_percent)}</div>
      <div class="kpi bad"><div class="label">Przeterminowane</div><div class="value">${d.counts.overdue}</div></div>
      <div class="kpi warn"><div class="label">Wygasa ≤ 30 dni</div><div class="value">${d.counts.expiring}</div></div>
      <div class="kpi todo"><div class="label">Nieukończone (w terminie)</div><div class="value">${d.counts.incomplete}</div></div>
      <div class="kpi"><div class="label">Wiadomości w kolejce</div><div class="value">${d.pending_messages}</div><a class="small" href="#przypomnienia">Przypomnienia →</a></div>
      <div class="kpi"><div class="label">Pytania do przeglądu</div><div class="value">${d.drafts_to_review}</div><a class="small" href="#szkolenia">Szkolenia →</a></div>
    </div>
    <div class="grid grid-2">
      <section class="card">
        <div class="card-head"><h2>Przeterminowane</h2>${chip("overdue", d.overdue.length)}</div>
        ${d.overdue.length ? `<div class="table-wrap"><table>
          <thead><tr><th>Pracownik</th><th>Szkolenie</th><th>Powód</th><th class="nowrap">Opóźnienie</th></tr></thead>
          <tbody>${d.overdue.map((o) => `<tr>
            <td><a class="nowrap" href="#moje/${o.employee_id}">${esc(o.full_name)}</a><div class="small muted">${esc(o.department)}</div></td>
            <td>${esc(o.training_title)}</td>
            <td class="small">${esc(o.reason)} ${fmtDate(o.since)}</td>
            <td class="nowrap"><strong>${o.days}</strong> ${plural(o.days, "dzień", "dni", "dni")}</td></tr>`).join("")}
          </tbody></table></div>` : `<p class="empty">Brak przeterminowanych szkoleń.</p>`}
      </section>
      <section class="card">
        <div class="card-head"><h2>Wygasa w ciągu 30 dni</h2>${chip("expiring", d.expiring.length)}</div>
        ${d.expiring.length ? `<div class="table-wrap"><table>
          <thead><tr><th>Pracownik</th><th>Szkolenie</th><th>Ważne do</th></tr></thead>
          <tbody>${d.expiring.map((o) => `<tr>
            <td><a class="nowrap" href="#moje/${o.employee_id}">${esc(o.full_name)}</a><div class="small muted">${esc(o.department)}</div></td>
            <td>${esc(o.training_title)}</td>
            <td class="nowrap">${fmtDate(o.valid_until)}<div class="small muted">za ${o.days_left} ${plural(o.days_left, "dzień", "dni", "dni")}</div></td></tr>`).join("")}
          </tbody></table></div>` : `<p class="empty">Nic nie wygasa w najbliższych 30 dniach.</p>`}
      </section>
      <section class="card"><h2>Zgodność według szkolenia</h2>${bars(d.by_training)}</section>
      <section class="card"><h2>Zgodność według działu</h2>${bars(d.by_department)}</section>
    </div>`;

  document.getElementById("dep").onchange = (e) => { sessionStorage.setItem("dep", e.target.value); renderDashboard(); };
  document.getElementById("run-daily").onclick = guard(runDaily(renderDashboard));
}

function runDaily(after) {
  return async () => {
    const r = await api("/api/jobs/daily", { method: "POST" });
    toast(`Przegląd dzienny: nowe przypisania ${r.created}, odnowienia ${r.renewals}, nowe wiadomości ${r.reminders_queued}.`);
    await after();
  };
}

// --- Pracownicy --------------------------------------------------------------------------

async function renderEmployees() {
  const [employees, deps] = await Promise.all([api("/api/employees"), api("/api/departments")]);
  const positions = [...new Set(employees.map((e) => e.position))].sort();
  app.innerHTML = `
    <div class="card-head"><h1>Pracownicy</h1><span class="muted">${employees.length} ${plural(employees.length, "osoba", "osoby", "osób")}</span></div>
    <section class="card">
      <div class="table-wrap"><table>
        <thead><tr><th>Imię i nazwisko</th><th>Dział</th><th class="hide-sm">Stanowisko</th><th class="hide-sm">Zatrudnienie</th><th class="hide-sm">Przełożony</th><th>Zgodność</th><th></th></tr></thead>
        <tbody>${employees.map((e) => `<tr>
          <td><strong>${esc(e.full_name)}</strong><div class="small muted">${esc(e.email)}</div></td>
          <td>${esc(e.department)}</td>
          <td class="hide-sm">${esc(e.position)}</td>
          <td class="hide-sm nowrap">${fmtDate(e.hire_date)}</td>
          <td class="hide-sm">${esc(e.manager_name || "—")}</td>
          <td style="min-width:110px"><strong>${e.compliance_percent ?? "—"}%</strong>${pctBar(e.compliance_percent ?? 0)}</td>
          <td><a class="btn small" href="#moje/${e.id}">Szkolenia</a></td></tr>`).join("")}
        </tbody></table></div>
    </section>
    <section class="card">
      <h2>Dodaj pracownika</h2>
      <p class="muted small">Po zapisaniu system przypisze szkolenia z reguł (termin = data zatrudnienia + liczba dni z reguły).</p>
      <form id="emp-form">
        <div class="form-grid">
          <div><label for="f-name">Imię i nazwisko</label><input id="f-name" name="full_name" required minlength="3"></div>
          <div><label for="f-email">E-mail</label><input id="f-email" name="email" type="email" required></div>
          <div><label for="f-dep">Dział</label><input id="f-dep" name="department" list="dl-dep" required></div>
          <div><label for="f-pos">Stanowisko</label><input id="f-pos" name="position" list="dl-pos" required></div>
          <div><label for="f-hire">Data zatrudnienia</label><input id="f-hire" name="hire_date" type="date" required value="${state.health?.today || ""}"></div>
          <div><label for="f-man">Przełożony</label><select id="f-man" name="manager_id"><option value="">— brak —</option>
            ${employees.map((e) => `<option value="${e.id}">${esc(e.full_name)} (${esc(e.department)})</option>`).join("")}</select></div>
        </div>
        <datalist id="dl-dep">${deps.map((d) => `<option value="${esc(d)}">`).join("")}</datalist>
        <datalist id="dl-pos">${positions.map((p) => `<option value="${esc(p)}">`).join("")}</datalist>
        <div class="form-actions"><button class="primary" type="submit">Dodaj i przypisz szkolenia</button></div>
      </form>
      <div id="emp-result"></div>
    </section>`;

  document.getElementById("emp-form").onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const body = formData(ev.target);
    body.manager_id = body.manager_id ? Number(body.manager_id) : null;
    const r = await api("/api/employees", { method: "POST", body });
    toast(`Dodano: ${r.employee.full_name}`);
    await renderEmployees();
    document.getElementById("emp-result").innerHTML = `<div class="notice ok">
      <strong>${esc(r.employee.full_name)}</strong> — automatycznie przypisano ${r.assignments_created.length} ${plural(r.assignments_created.length, "szkolenie", "szkolenia", "szkoleń")}:
      <ul>${r.assignments_created.map((a) => `<li>${esc(a.training_title)} — termin ${fmtDate(a.due_date)}</li>`).join("") || "<li>brak pasujących reguł</li>"}</ul>
      <a href="#moje/${r.employee.id}">Zobacz szkolenia pracownika →</a></div>`;
  });
}

// --- Szkolenia ---------------------------------------------------------------------------

async function renderTrainings(id) {
  const trainings = await api("/api/trainings");
  const selected = Number(id) || trainings[0]?.id;
  app.innerHTML = `
    <div class="card-head"><h1>Szkolenia</h1></div>
    <div class="split">
      <aside>${trainings.map((t) => `
        <a class="list-item ${t.id === selected ? "active" : ""}" href="#szkolenia/${t.id}">
          <div class="title">${esc(t.title)}</div>
          <div class="row">${chip("info", t.category)} ${chip("", t.mode_label)}
            ${t.question_counts.draft ? chip("draft", `szkice: ${t.question_counts.draft}`) : ""}</div>
        </a>`).join("")}
        <details class="card" style="margin-top:12px"><summary>Nowe szkolenie</summary>
          <form id="new-training" class="stack" style="margin-top:10px">
            <div><label for="nt-title">Tytuł</label><input id="nt-title" name="title" required minlength="3"></div>
            <div><label for="nt-cat">Kategoria</label><input id="nt-cat" name="category" required list="dl-cat" placeholder="np. BHP"></div>
            <datalist id="dl-cat"><option value="BHP"><option value="RODO"><option value="Sygnaliści"><option value="Antymobbing"></datalist>
            <div><label for="nt-mode">Tryb</label><select id="nt-mode" name="mode"><option value="elearning">e-learning (test online)</option><option value="classroom">stacjonarne (potwierdzenie prowadzącego)</option></select></div>
            <div><label for="nt-val">Ważność (miesiące, puste = bezterminowo)</label><input id="nt-val" name="validity_months" type="number" min="1" max="240"></div>
            <div><label for="nt-thr">Próg zaliczenia (%)</label><input id="nt-thr" name="pass_threshold" type="number" min="1" max="100" value="80"></div>
            <div><label for="nt-mat">Materiał</label><textarea id="nt-mat" name="material" rows="4"></textarea></div>
            <button class="primary" type="submit">Utwórz</button>
          </form>
        </details>
      </aside>
      <section id="training-detail">${selected ? "" : `<p class="empty">Brak szkoleń.</p>`}</section>
    </div>`;

  document.getElementById("new-training").onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const b = formData(ev.target);
    b.validity_months = b.validity_months ? Number(b.validity_months) : null;
    b.pass_threshold = Number(b.pass_threshold || 80);
    const t = await api("/api/trainings", { method: "POST", body: b });
    toast(`Utworzono szkolenie: ${t.title}`);
    location.hash = `#szkolenia/${t.id}`;
  });
  if (selected) await renderTrainingDetail(selected);
}

async function renderTrainingDetail(id) {
  const box = document.getElementById("training-detail");
  const t = await api(`/api/trainings/${id}`);
  const isElearning = t.mode === "elearning";
  box.innerHTML = `
    <div class="card">
      <h2>${esc(t.title)}</h2>
      <div class="row">${chip("info", t.category)} ${chip("", t.mode_label)}
        ${chip("", t.validity_months ? `ważność ${t.validity_months} mies.` : "bezterminowo")}
        ${isElearning ? chip("", `próg ${t.pass_threshold}%`) + chip("", `pytań w teście: ${t.questions_per_test}`) : ""}</div>
      ${isElearning ? "" : `<div class="notice" style="margin-top:12px">Szkolenie stacjonarne — zalicza je potwierdzenie prowadzącego (widok „Moje szkolenia”), bez testu online.</div>`}
    </div>
    <div class="card">
      <div class="card-head"><h2>Reguły przypisań</h2><span class="muted small">puste pole = dowolny</span></div>
      ${t.rules.length ? `<div class="table-wrap"><table><thead><tr><th>Dział</th><th>Stanowisko</th><th>Termin od zatrudnienia</th><th></th></tr></thead>
        <tbody>${t.rules.map((r) => `<tr><td>${esc(r.department || "każdy")}</td><td>${esc(r.position || "każde")}</td>
          <td>${r.due_days} ${plural(r.due_days, "dzień", "dni", "dni")}</td>
          <td><button class="small danger" data-del-rule="${r.id}">Usuń</button></td></tr>`).join("")}</tbody></table></div>`
        : `<p class="empty">Brak reguł — szkolenie nikomu nie jest przypisywane automatycznie.</p>`}
      <form id="rule-form" class="form-grid" style="margin-top:12px">
        <div><label for="r-dep">Dział</label><input id="r-dep" name="department" placeholder="np. Magazyn"></div>
        <div><label for="r-pos">Stanowisko</label><input id="r-pos" name="position" placeholder="np. Magazynier"></div>
        <div><label for="r-days">Termin (dni)</label><input id="r-days" name="due_days" type="number" min="0" max="730" value="30" required></div>
        <div style="align-self:end"><button class="primary" type="submit">Dodaj regułę</button></div>
      </form>
    </div>
    <div class="card">
      <details><summary>Materiał szkoleniowy (${t.material.length} znaków)</summary>
        <form id="material-form" style="margin-top:10px">
          <textarea name="material" rows="14">${esc(t.material)}</textarea>
          <div class="form-actions"><button type="submit">Zapisz materiał</button>
          <span class="muted small">Po zmianie materiału cytaty pytań są weryfikowane ponownie.</span></div>
        </form>
      </details>
    </div>
    ${isElearning ? `<div class="card" id="questions-card">
      <div class="card-head"><h2>Pytania testowe</h2>
        <div class="row">
          <select id="gen-count" aria-label="Liczba pytań" style="width:auto">
            <option value="3">3 pytania</option><option value="5" selected>5 pytań</option><option value="10">10 pytań</option></select>
          <button class="primary" id="gen-btn">Generuj pytania</button>
          <button id="add-q">Dodaj ręcznie</button>
        </div></div>
      <p class="muted small">Nowe pytania mają status „szkic”. Do testu trafiają wyłącznie pytania zatwierdzone przez HR.
      Każde pytanie ma cytat źródłowy sprawdzany jako fragment materiału.</p>
      <div id="gen-result"></div>
      <div class="subtabs" id="q-tabs"></div>
      <div id="q-list"></div>
    </div>` : ""}`;

  box.querySelectorAll("[data-del-rule]").forEach((b) => (b.onclick = guard(async () => {
    const r = await api(`/api/rules/${b.dataset.delRule}`, { method: "DELETE" });
    toast(`Usunięto regułę. Anulowane otwarte przypisania: ${r.sync.cancelled}.`);
    renderTrainingDetail(id);
  })));
  document.getElementById("rule-form").onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const b = formData(ev.target);
    const r = await api("/api/rules", { method: "POST", body: { training_id: t.id, department: b.department || null, position: b.position || null, due_days: Number(b.due_days) } });
    toast(`Dodano regułę. Nowe przypisania: ${r.sync.created.length}.`);
    renderTrainingDetail(id);
  });
  document.getElementById("material-form").onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const r = await api(`/api/trainings/${t.id}`, { method: "PATCH", body: { material: formData(ev.target).material } });
    toast(r.questions_reverted ? `Zapisano. ${r.questions_reverted} zatwierdzonych pytań wróciło do szkicu (cytat zniknął z materiału).` : "Zapisano materiał.");
    renderTrainingDetail(id);
  });
  if (isElearning) {
    document.getElementById("gen-btn").onclick = guard(async (ev) => {
      ev.target.disabled = true;
      ev.target.textContent = "Generuję…";
      try {
        const r = await api(`/api/trainings/${t.id}/questions/generate`, { method: "POST", body: { count: Number(document.getElementById("gen-count").value) } });
        state.questionFilter = "draft";
        await renderQuestions(t);
        showGenerationResult(r);
      } finally {
        ev.target.disabled = false;
        ev.target.textContent = "Generuj pytania";
      }
    });
    document.getElementById("add-q").onclick = () => {
      const list = document.getElementById("q-list");
      const holder = document.createElement("div");
      list.prepend(holder);
      editQuestion(holder, t, { text: "", options: ["", "", "", ""], correct_index: 0, explanation: "", source_quote: "" }, true);
    };
    await renderQuestions(t);
  }
}

function showGenerationResult(r) {
  const parts = [`<strong>Źródło: ${r.source === "ai" ? "AI (Claude)" : "generator offline"}</strong> — dodano ${r.created.length} ${plural(r.created.length, "szkic", "szkice", "szkiców")}.`];
  if (r.injection_suspected) parts.push(`<div class="notice bad" style="margin:8px 0 0">Uwaga: materiał zawiera tekst wyglądający na próbę sterowania modelem (prompt injection). Sprawdź treść materiału.</div>`);
  if (r.warnings.length) parts.push(`<ul>${r.warnings.map((w) => `<li>${esc(w)}</li>`).join("")}</ul>`);
  if (r.rejected.length) parts.push(`<div>Odrzucone propozycje AI (${r.rejected.length}):</div><ul>${r.rejected.map((x) => `<li>${esc(x.question)} — <em>${esc(x.reason)}</em></li>`).join("")}</ul>`);
  document.getElementById("gen-result").innerHTML = `<div class="notice ${r.warnings.length || r.rejected.length ? "warn" : "ok"}">${parts.join("")}</div>`;
}

async function renderQuestions(t) {
  const all = await api(`/api/trainings/${t.id}/questions`);
  const counts = { draft: 0, approved: 0, rejected: 0 };
  all.forEach((q) => counts[q.status]++);
  const filters = [["draft", `Szkice (${counts.draft})`], ["approved", `Zatwierdzone (${counts.approved})`], ["rejected", `Odrzucone (${counts.rejected})`], ["", `Wszystkie (${all.length})`]];
  const tabs = document.getElementById("q-tabs");
  tabs.innerHTML = filters.map(([k, label]) => `<button class="small ${state.questionFilter === k ? "active" : ""}" data-f="${k}">${label}</button>`).join("");
  tabs.querySelectorAll("button").forEach((b) => (b.onclick = () => { state.questionFilter = b.dataset.f; renderQuestions(t); }));
  const list = document.getElementById("q-list");
  const shown = all.filter((q) => !state.questionFilter || q.status === state.questionFilter);
  list.innerHTML = shown.length ? "" : `<p class="empty">Brak pytań w tym widoku.</p>`;
  shown.forEach((q) => {
    const holder = document.createElement("div");
    list.appendChild(holder);
    showQuestion(holder, t, q);
  });
}

function showQuestion(holder, t, q) {
  holder.className = "question";
  holder.innerHTML = `
    <div class="row">${chip(q.status, Q_STATUS[q.status])} ${chip(q.origin === "ai" ? "ai" : "", "źródło: " + ORIGIN[q.origin])}
      ${q.reviewed_by ? `<span class="small muted">przejrzał(a): ${esc(q.reviewed_by)}</span>` : ""}</div>
    <div class="q-text">${esc(q.text)}</div>
    <ol>${q.options.map((o, i) => `<li class="${i === q.correct_index ? "correct" : ""}">${esc(o)}${i === q.correct_index ? " ✓" : ""}</li>`).join("")}</ol>
    ${q.explanation ? `<div class="small"><strong>Wyjaśnienie:</strong> ${esc(q.explanation)}</div>` : ""}
    <div class="quote ${q.quote_verified ? "verified" : "unverified"}">${q.quote_verified ? "✓ cytat zweryfikowany w materiale" : "✗ cytat nie występuje w materiale"}: „${esc(q.source_quote || "—")}”</div>
    <div class="form-actions">
      ${q.status !== "approved" ? `<button class="small ok" data-a="approve">Zatwierdź</button>` : ""}
      <button class="small" data-a="edit">Edytuj</button>
      ${q.status !== "rejected" ? `<button class="small" data-a="reject">Odrzuć</button>` : ""}
      <button class="small danger" data-a="delete">Usuń</button>
    </div>`;
  const act = {
    approve: async () => { await api(`/api/questions/${q.id}/approve`, { method: "POST", body: { reviewer: "HR" } }); toast("Zatwierdzono pytanie."); },
    reject: async () => { await api(`/api/questions/${q.id}/reject`, { method: "POST", body: { reviewer: "HR" } }); toast("Odrzucono pytanie."); },
    delete: async () => { if (!confirm("Usunąć pytanie?")) return; await api(`/api/questions/${q.id}`, { method: "DELETE" }); toast("Usunięto pytanie."); },
  };
  holder.querySelectorAll("[data-a]").forEach((b) => (b.onclick = guard(async () => {
    if (b.dataset.a === "edit") return editQuestion(holder, t, q, false);
    await act[b.dataset.a]();
    renderQuestions(t);
  })));
}

function editQuestion(holder, t, q, isNew) {
  holder.className = "question";
  holder.innerHTML = `
    <form class="stack">
      <div><label>Treść pytania</label><textarea name="text" rows="3" required>${esc(q.text)}</textarea></div>
      <div class="edit-options"><label>Odpowiedzi (zaznacz poprawną)</label>
        ${q.options.map((o, i) => `<div class="opt"><input type="radio" name="correct" value="${i}" ${i === q.correct_index ? "checked" : ""} aria-label="Poprawna odpowiedź ${i + 1}">
          <input name="opt${i}" value="${esc(o)}" required placeholder="Odpowiedź ${String.fromCharCode(65 + i)}"></div>`).join("")}</div>
      <div><label>Wyjaśnienie</label><textarea name="explanation" rows="2">${esc(q.explanation)}</textarea></div>
      <div><label>Cytat źródłowy (dosłowny fragment materiału)</label><textarea name="source_quote" rows="2">${esc(q.source_quote)}</textarea></div>
      <div class="form-actions"><button class="primary" type="submit">Zapisz jako szkic</button><button type="button" data-cancel>Anuluj</button></div>
    </form>`;
  holder.querySelector("[data-cancel]").onclick = () => (isNew ? holder.remove() : showQuestion(holder, t, q));
  holder.querySelector("form").onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const f = formData(ev.target);
    const body = { text: f.text, options: [0, 1, 2, 3].map((i) => f["opt" + i]), correct_index: Number(f.correct), explanation: f.explanation, source_quote: f.source_quote };
    if (isNew) await api(`/api/trainings/${t.id}/questions`, { method: "POST", body });
    else await api(`/api/questions/${q.id}`, { method: "PUT", body });
    toast("Zapisano pytanie jako szkic — zatwierdź je, aby trafiło do testu.");
    state.questionFilter = "draft";
    renderQuestions(t);
  });
}

// --- Moje szkolenia ----------------------------------------------------------------------

async function renderMyTrainings(id) {
  const employees = await api("/api/employees");
  const employeeId = Number(id) || state.currentEmployee || employees[0]?.id;
  state.currentEmployee = employeeId;
  const data = await api(`/api/employees/${employeeId}/trainings`);
  const e = data.employee;
  app.innerHTML = `
    <div class="card-head">
      <div><h1>Moje szkolenia</h1><div class="muted">${esc(e.full_name)} · ${esc(e.position)} · dział ${esc(e.department)}</div></div>
      <div><label for="who">Widok pracownika (symulacja logowania)</label>
        <select id="who">${employees.map((x) => `<option value="${x.id}" ${x.id === employeeId ? "selected" : ""}>${esc(x.full_name)}</option>`).join("")}</select></div>
    </div>
    <div id="my-list">${data.items.length ? data.items.map(trainingCard).join("") : `<p class="empty">Brak wymaganych szkoleń.</p>`}</div>
    <section class="card"><h2>Zaświadczenia</h2>
      ${data.certificates.length ? `<div class="table-wrap"><table><thead><tr><th>Numer</th><th>Szkolenie</th><th>Wydane</th><th>Ważne do</th><th></th></tr></thead>
      <tbody>${data.certificates.map((c) => `<tr><td class="nowrap">${esc(c.number)}</td><td>${esc(c.training_title)}</td><td class="nowrap">${fmtDate(c.issued_on)}</td>
        <td class="nowrap">${c.valid_until ? fmtDate(c.valid_until) : "bezterminowo"}</td>
        <td><a class="btn small" href="/certificates/${c.id}" target="_blank" rel="noopener">Drukuj</a></td></tr>`).join("")}</tbody></table></div>`
        : `<p class="empty">Brak zaświadczeń.</p>`}
    </section>`;

  document.getElementById("who").onchange = (ev) => { location.hash = `#moje/${ev.target.value}`; };
  app.querySelectorAll("[data-start]").forEach((b) => (b.onclick = guard(async () => {
    const quiz = await api(`/api/assignments/${b.dataset.start}/attempts`, { method: "POST" });
    sessionStorage.setItem(`quiz-${quiz.attempt_id}`, JSON.stringify(quiz));
    location.hash = `#test/${quiz.attempt_id}`;
  })));
  app.querySelectorAll("[data-material]").forEach((b) => (b.onclick = guard(async () => {
    const box = document.getElementById(`mat-${b.dataset.material}`);
    if (box.innerHTML) { box.innerHTML = ""; return; }
    const t = await api(`/api/trainings/${b.dataset.material}`);
    box.innerHTML = `<div class="material">${esc(t.material || "Brak materiału.")}</div>`;
  })));
  app.querySelectorAll("form[data-confirm]").forEach((f) => (f.onsubmit = guard(async (ev) => {
    ev.preventDefault();
    const b = formData(ev.target);
    const cert = await api(`/api/assignments/${f.dataset.confirm}/confirm`, { method: "POST", body: { trainer: b.trainer, session_date: b.session_date } });
    toast(`Potwierdzono udział. Zaświadczenie ${cert.number}.`);
    renderMyTrainings(employeeId);
  })));
}

function trainingCard(item) {
  const t = item.training;
  const dates = [];
  if (item.completed_on) dates.push(`ukończone ${fmtDate(item.completed_on)}`);
  if (item.completed_on) dates.push(item.valid_until ? `ważne do ${fmtDate(item.valid_until)}` : "bezterminowo");
  if (item.due_date) dates.push(`termin ${fmtDate(item.due_date)}`);
  const attempts = item.attempts.length ? `<div class="small muted">Podejścia: ${item.attempts.map((a) => `${a.percent}% ${a.passed ? "✓" : "✗"} (${fmtDate(a.submitted_at)})`).join(", ")}</div>` : "";
  let actions = "";
  if (item.assignment_id && t.mode === "elearning") {
    actions = `<button class="small" data-material="${t.id}">Materiał</button>` +
      (item.can_take_test ? `<button class="small primary" data-start="${item.assignment_id}">Rozpocznij test</button>`
        : `<span class="small muted">Test niedostępny — HR nie zatwierdził jeszcze pytań.</span>`);
  } else if (item.needs_confirmation) {
    actions = `<form class="row" data-confirm="${item.assignment_id}">
      <span class="small muted">Wymaga potwierdzenia prowadzącego (BHP/HR):</span>
      <input name="trainer" placeholder="Prowadzący" required minlength="3" style="width:180px">
      <input name="session_date" type="date" required value="${state.health?.today || ""}" style="width:160px">
      <button class="small primary" type="submit">Potwierdź udział</button></form>`;
  }
  const cert = item.certificate_id ? `<a class="btn small" href="/certificates/${item.certificate_id}" target="_blank" rel="noopener">Zaświadczenie ${esc(item.certificate_number)}</a>` : "";
  return `<div class="training-card ${item.status}">
    <div class="row"><strong>${esc(t.title)}</strong>${statusChip(item.status)}<span class="spacer"></span>${chip("", t.mode_label)}</div>
    <div class="meta">${esc(t.category)} · ${dates.join(" · ") || "bez terminu"}${t.mode === "elearning" ? ` · próg ${t.pass_threshold}%` : ""}</div>
    ${attempts}
    <div class="row" style="margin-top:8px">${actions}${cert}</div>
    <div id="mat-${t.id}" style="margin-top:8px"></div>
  </div>`;
}

async function renderQuiz(attemptId) {
  const quiz = JSON.parse(sessionStorage.getItem(`quiz-${attemptId}`) || "null");
  if (!quiz) {
    app.innerHTML = `<div class="notice warn">To podejście nie jest już dostępne w tej karcie. Rozpocznij test ponownie z listy „Moje szkolenia”.</div>`;
    return;
  }
  app.innerHTML = `
    <div class="card-head"><div><h1>${esc(quiz.training.title)}</h1>
      <div class="muted">${quiz.total} ${plural(quiz.total, "pytanie", "pytania", "pytań")} · próg zaliczenia ${quiz.training.pass_threshold}% · kolejność pytań i odpowiedzi losowa</div></div>
      <a class="btn" href="#moje/${state.currentEmployee || ""}">Wróć</a></div>
    <form id="quiz-form">
      ${quiz.questions.map((q, n) => `<fieldset class="quiz-q" style="margin:0 0 12px">
        <legend class="q-text">${n + 1}. ${esc(q.text)}</legend>
        ${q.options.map((o, i) => `<label class="opt"><input type="radio" name="q${q.id}" value="${i}"> <span>${esc(o)}</span></label>`).join("")}
      </fieldset>`).join("")}
      <div class="row"><button class="primary" type="submit">Zakończ i sprawdź</button><span class="muted small" id="progress"></span></div>
    </form>`;
  const form = document.getElementById("quiz-form");
  const progress = () => {
    const answered = quiz.questions.filter((q) => form.querySelector(`input[name=q${q.id}]:checked`)).length;
    document.getElementById("progress").textContent = `Odpowiedziano: ${answered}/${quiz.total}`;
    return answered;
  };
  form.onchange = progress;
  progress();
  form.onsubmit = guard(async (ev) => {
    ev.preventDefault();
    if (progress() < quiz.total && !confirm("Nie wszystkie pytania mają odpowiedź. Brak odpowiedzi liczy się jako błąd. Zakończyć?")) return;
    const answers = quiz.questions.map((q) => {
      const c = form.querySelector(`input[name=q${q.id}]:checked`);
      return c ? { question_id: q.id, option_index: Number(c.value) } : null;
    }).filter(Boolean);
    const r = await api(`/api/attempts/${attemptId}/submit`, { method: "POST", body: { answers } });
    sessionStorage.removeItem(`quiz-${attemptId}`);
    showQuizResult(quiz, r);
  });
}

function showQuizResult(quiz, r) {
  const cert = r.certificate;
  app.innerHTML = `
    <div class="card">
      <div class="row"><div class="score ${r.passed ? "pass" : "fail"}">${r.percent}%</div>
        <div><strong>${r.passed ? "Test zaliczony" : "Test niezaliczony"}</strong>
        <div class="muted">${r.score} z ${r.total} poprawnych · próg ${r.pass_threshold}%</div></div></div>
      ${cert ? `<div class="notice ok" style="margin-top:12px">Wydano zaświadczenie <strong>${esc(cert.number)}</strong>
        · ważne ${cert.valid_until ? "do " + fmtDate(cert.valid_until) : "bezterminowo"} · kod weryfikacyjny <span class="code">${esc(cert.verification_code)}</span>
        <div class="form-actions"><a class="btn primary" href="/certificates/${cert.id}" target="_blank" rel="noopener">Drukuj zaświadczenie</a></div></div>`
        : `<div class="notice warn" style="margin-top:12px">Przeczytaj ponownie materiał i spróbuj jeszcze raz. Poprawne odpowiedzi pokazujemy dopiero po zaliczeniu.</div>`}
      <div class="form-actions"><a class="btn" href="#moje/${state.currentEmployee || ""}">Wróć do moich szkoleń</a></div>
    </div>
    ${r.items.map((it, n) => `<div class="quiz-q ${it.correct ? "good" : "bad"}">
      <div class="q-text">${n + 1}. ${esc(it.text)} ${it.correct ? chip("valid", "dobrze") : chip("overdue", "źle")}</div>
      ${it.options.map((o, i) => {
        const cls = it.correct_index === i ? "correct" : (it.chosen_index === i && !it.correct ? "wrong" : "");
        return `<div class="opt ${cls}" style="padding:6px 10px;border-radius:8px">${i === it.chosen_index ? "● " : "○ "}${esc(o)}</div>`;
      }).join("")}
      ${it.explanation ? `<div class="small" style="margin-top:6px"><strong>Wyjaśnienie:</strong> ${esc(it.explanation)}</div>` : ""}
    </div>`).join("")}`;
}

// --- Raport ------------------------------------------------------------------------------

async function renderReport() {
  const dep = sessionStorage.getItem("dep-report") || "";
  const q = dep ? `?department=${encodeURIComponent(dep)}` : "";
  const [m, depOpts] = await Promise.all([api("/api/report" + q), departmentOptions(dep)]);
  app.innerHTML = `
    <div class="card-head"><div><h1>Raport pod kontrolę</h1><div class="muted">Macierz pracownik × szkolenie · stan na ${fmtDate(m.today)}</div></div>
      <div class="row"><select id="dep-r" aria-label="Filtr działu" style="width:auto">${depOpts}</select>
        <a class="btn" href="/api/report.csv${q}">Eksport CSV</a>
        <a class="btn primary" href="/report/print${q}" target="_blank" rel="noopener">Wersja do druku</a></div></div>
    <section class="card"><div class="table-wrap"><table class="matrix">
      <thead><tr><th>Pracownik</th>${m.trainings.map((t) => `<th>${esc(t.title)}</th>`).join("")}</tr></thead>
      <tbody>${m.rows.map((r) => `<tr>
        <td><strong>${esc(r.employee.full_name)}</strong><span class="detail">${esc(r.employee.position)} · ${esc(r.employee.department)}</span>
          <span class="detail">zgodność ${r.compliance_percent}%</span></td>
        ${m.trainings.map((t) => matrixCell(r.cells[t.id])).join("")}</tr>`).join("")}
      </tbody></table></div>
      <div class="legend">${["valid", "expiring", "overdue", "incomplete", "not_applicable"].map(statusChip).join("")}</div>
    </section>`;
  document.getElementById("dep-r").onchange = (e) => { sessionStorage.setItem("dep-report", e.target.value); renderReport(); };
}

function matrixCell(c) {
  if (!c.required) return `<td>${statusChip("not_applicable")}</td>`;
  const d = [];
  if (c.completed_on) d.push(`ukończone ${fmtDate(c.completed_on)}`, c.valid_until ? `ważne do ${fmtDate(c.valid_until)}` : "bezterminowo");
  if (c.due_date) d.push(`termin ${fmtDate(c.due_date)}`);
  if (c.certificate_number) d.push(`nr ${c.certificate_number}`);
  return `<td>${statusChip(c.status)}${d.map((x) => `<span class="detail">${esc(x)}</span>`).join("")}</td>`;
}

// --- Przypomnienia -----------------------------------------------------------------------

async function renderOutbox() {
  const f = state.outboxFilter;
  const msgs = await api("/api/outbox" + (f ? `?status=${f}` : ""));
  app.innerHTML = `
    <div class="card-head"><div><h1>Przypomnienia (outbox)</h1>
      <div class="muted">Kolejka wiadomości e-mail. Wysyłka jest atrapą — w produkcji podłącz SMTP lub API poczty.</div></div>
      <div class="row">
        <select id="ob-f" aria-label="Filtr statusu" style="width:auto">
          <option value="" ${f === "" ? "selected" : ""}>Wszystkie</option>
          <option value="pending" ${f === "pending" ? "selected" : ""}>Oczekujące</option>
          <option value="sent" ${f === "sent" ? "selected" : ""}>Wysłane</option></select>
        <button id="ob-daily">Uruchom przegląd dzienny</button>
        <button class="primary" id="ob-send">Wyślij oczekujące (symulacja)</button></div></div>
    <div class="notice small">Etapy: 30, 7 i 1 dzień przed terminem, po terminie (pracownik) oraz eskalacja do przełożonego. Każdy etap trafia do kolejki najwyżej raz na przypisanie — ponowne uruchomienie przeglądu nie dubluje wiadomości.</div>
    <section class="card">${msgs.length ? `<div class="table-wrap"><table>
      <thead><tr><th>Utworzono</th><th>Typ</th><th>Odbiorca</th><th>Temat i treść</th><th>Status</th></tr></thead>
      <tbody>${msgs.map((m) => `<tr>
        <td class="nowrap small">${fmtDateTime(m.created_at)}</td>
        <td>${chip(m.kind === "overdue" ? "overdue-kind" : m.kind, KIND[m.kind])}</td>
        <td>${esc(m.recipient_name)}<div class="small muted">${esc(m.recipient_email)}</div></td>
        <td><details><summary>${esc(m.subject)}</summary><div class="outbox-body">${esc(m.body)}</div></details></td>
        <td>${chip(m.status, m.status === "sent" ? "wysłano" : "oczekuje")}${m.sent_at ? `<div class="small muted">${fmtDateTime(m.sent_at)}</div>` : ""}</td></tr>`).join("")}
      </tbody></table></div>` : `<p class="empty">Brak wiadomości.</p>`}</section>`;
  document.getElementById("ob-f").onchange = (e) => { state.outboxFilter = e.target.value; renderOutbox(); };
  document.getElementById("ob-daily").onclick = guard(runDaily(renderOutbox));
  document.getElementById("ob-send").onclick = guard(async () => {
    const r = await api("/api/outbox/send", { method: "POST" });
    toast(`Oznaczono jako wysłane: ${r.sent}.`);
    renderOutbox();
  });
}

// --- Weryfikacja -------------------------------------------------------------------------

async function renderVerify(code) {
  app.innerHTML = `
    <div class="card" style="max-width:640px">
      <h1>Weryfikacja zaświadczenia</h1>
      <p class="muted">Wpisz kod z wydruku zaświadczenia (np. ABCD-EFGH-JKLM). Wynik pokazuje tylko dane potrzebne do potwierdzenia autentyczności.</p>
      <form id="vf" class="row"><input name="code" required placeholder="Kod weryfikacyjny" value="${esc(code || "")}" style="flex:1;min-width:200px" class="code">
        <button class="primary" type="submit">Sprawdź</button></form>
      <div id="vr" style="margin-top:14px"></div>
    </div>`;
  const check = async (value) => {
    const box = document.getElementById("vr");
    try {
      const r = await api(`/api/verify/${encodeURIComponent(value)}`);
      box.innerHTML = `<div class="notice ${r.is_valid ? "ok" : "warn"}"><strong>Zaświadczenie istnieje — ${esc(r.status_label)}</strong>
        <div>nr ${esc(r.number)} · ${esc(r.full_name)}</div><div>${esc(r.training_title)}</div>
        <div>ukończone ${fmtDate(r.issued_on)} · ${r.valid_until ? "ważne do " + fmtDate(r.valid_until) : "bezterminowo"}</div></div>`;
    } catch (err) {
      box.innerHTML = `<div class="notice bad">${esc(err.message)}</div>`;
    }
  };
  document.getElementById("vf").onsubmit = (ev) => { ev.preventDefault(); check(formData(ev.target).code); };
  if (code) check(code);
}

init();

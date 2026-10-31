"use strict";

// Prosty klient SPA: routing po hashu, bez zależności. Każdy tekst od użytkownika lub modelu
// przechodzi przez esc() przed wstawieniem do HTML.

const app = document.getElementById("app");
const STAR = ["S", "T", "A", "R"];
const STAR_NAMES = { S: "Sytuacja", T: "Zadanie", A: "Działanie", R: "Rezultat" };
const STYLE_LABELS = { gadatliwy: "gadatliwy", zestresowany: "zestresowany", unikajacy_konkretow: "unikający konkretów" };
const TYPE_LABELS = {
  otwarte: "otwarte", zamkniete: "zamknięte", sugerujace: "sugerujące",
  wielokrotne: "wielokrotne", behawioralne: "behawioralne (STAR)", dopytujace: "dopytujące",
};
const GOOD_TYPES = new Set(["otwarte", "behawioralne", "dopytujace"]);
let status = { mode: "offline" };

function esc(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
const pct = (x) => `${Math.round((x || 0) * 100)}%`;
const initials = (name) => name.split(/\s+/).map((p) => p[0] || "").join("").slice(0, 2).toUpperCase();
const fmtDate = (iso) => {
  if (!iso) return "—";
  const d = new Date(iso);
  return isNaN(d) ? iso : d.toLocaleString("pl-PL", { dateStyle: "medium", timeStyle: "short" });
};
const sourceBadge = (src, aiText = "AI", offText = "offline") =>
  src === "ai" ? `<span class="badge badge--ai" title="Wynik modelu AI">${aiText}</span>`
               : `<span class="badge" title="Wynik reguł / skryptu offline">${offText}</span>`;

async function api(path, options = {}) {
  const res = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
  if (res.status === 204) return null;
  const isJson = (res.headers.get("content-type") || "").includes("json");
  const body = isJson ? await res.json() : await res.text();
  if (!res.ok) {
    const detail = isJson ? (body.bledy?.map((b) => b.komunikat).join(" ") || body.detail) : body;
    throw new Error(detail || `Błąd ${res.status}`);
  }
  return body;
}

function setNav(name) {
  document.querySelectorAll("[data-nav]").forEach((a) => a.classList.toggle("active", a.dataset.nav === name));
}

function showError(err) {
  app.innerHTML = `<div class="card"><p class="error">${esc(err.message || err)}</p><a class="btn btn--ghost" href="#/">Wróć do scenariuszy</a></div>`;
}

// ---------------- widok: scenariusze ----------------
async function viewHome() {
  setNav("home");
  const [scenarios, sessions] = await Promise.all([api("/api/scenarios"), api("/api/sessions")]);
  app.innerHTML = `
    <section>
      <div class="section__head">
        <div>
          <h1>Wybierz scenariusz rozmowy</h1>
          <p class="muted">Poprowadź rozmowę z kandydatem symulowanym przez komputer. Coach podpowiada na żywo, a po zakończeniu dostaniesz raport.</p>
        </div>
      </div>
      <div class="grid-cards">
        ${scenarios.map(scenarioCard).join("")}
      </div>
    </section>
    <section class="section">
      <div class="section__head">
        <h2>Ostatnie sesje</h2>
        <a href="#/sesje">Wszystkie sesje →</a>
      </div>
      ${sessionsTable(sessions.slice(0, 5))}
    </section>`;
  app.querySelectorAll("[data-start]").forEach((btn) =>
    btn.addEventListener("click", async () => {
      btn.disabled = true;
      try {
        const data = await api("/api/sessions", { method: "POST", body: JSON.stringify({ scenario_id: btn.dataset.start }) });
        location.hash = `#/sesja/${data.session.id}`;
      } catch (err) {
        btn.disabled = false;
        alert(err.message);
      }
    }));
  bindSessionActions();
}

function scenarioCard(s) {
  const diffClass = { łatwy: "badge--good", średni: "badge--warn", trudny: "badge--bad" }[s.difficulty] || "";
  return `
    <article class="card scenario">
      <div class="scenario__meta">
        <span class="badge ${diffClass}">poziom: ${esc(s.difficulty)}</span>
        <span class="badge">${s.facts_total} ukrytych faktów</span>
      </div>
      <div>
        <h2>${esc(s.title)}</h2>
        <p class="muted small">${esc(s.company)}</p>
        <p class="small">${esc(s.description)}</p>
      </div>
      <div class="scenario__candidate">
        <span class="avatar" aria-hidden="true">${esc(initials(s.candidate.name))}</span>
        <div class="small">
          <strong>${esc(s.candidate.name)}</strong> · styl: ${esc(STYLE_LABELS[s.candidate.style] || s.candidate.style)}<br>
          <span class="muted">${esc(s.candidate.background)}</span>
        </div>
      </div>
      <div>
        <div class="small muted">Kompetencje do zbadania:</div>
        <div class="chips">${s.competencies.map((c) => `<span class="badge" title="${esc(c.description)}">${esc(c.name)}</span>`).join("")}</div>
      </div>
      <button class="btn" data-start="${esc(s.id)}">Rozpocznij rozmowę</button>
    </article>`;
}

function sessionsTable(sessions) {
  if (!sessions.length) return `<div class="card empty">Nie masz jeszcze żadnych sesji. Wybierz scenariusz powyżej.</div>`;
  return `
    <div class="card table-wrap">
      <table>
        <thead><tr><th>Data</th><th>Scenariusz</th><th class="num">Pytań</th><th>Status</th><th class="num">Pokrycie</th><th class="num">Incydenty</th><th>Akcje</th></tr></thead>
        <tbody>
          ${sessions.map((s) => {
            const done = s.status === "zakonczona";
            const sum = s.summary || {};
            return `<tr>
              <td>${esc(fmtDate(s.created_at))}</td>
              <td>${esc(s.scenario_title)} ${s.ai_enabled ? '<span class="badge badge--ai">AI</span>' : '<span class="badge">offline</span>'}</td>
              <td class="num">${s.turns_count}</td>
              <td>${done ? '<span class="badge badge--good">zakończona</span>' : '<span class="badge badge--warn">w toku</span>'}</td>
              <td class="num">${done && sum.coverage_share != null ? pct(sum.coverage_share) : "—"}</td>
              <td class="num">${done ? esc(sum.incidents ?? 0) : "—"}</td>
              <td><div class="actions">
                ${done ? `<a class="btn btn--small" href="#/raport/${esc(s.id)}">Raport</a>` : `<a class="btn btn--small" href="#/sesja/${esc(s.id)}">Kontynuuj</a>`}
                <a class="btn btn--small btn--ghost" href="/api/sessions/${esc(s.id)}/export.md">Markdown</a>
                <button class="btn btn--small btn--danger" data-delete="${esc(s.id)}">Usuń</button>
              </div></td>
            </tr>`;
          }).join("")}
        </tbody>
      </table>
    </div>`;
}

function bindSessionActions() {
  app.querySelectorAll("[data-delete]").forEach((btn) =>
    btn.addEventListener("click", async () => {
      if (!confirm("Usunąć tę sesję razem z transkryptem i raportem?")) return;
      await api(`/api/sessions/${btn.dataset.delete}`, { method: "DELETE" });
      route();
    }));
}

async function viewSessions() {
  setNav("sessions");
  const sessions = await api("/api/sessions");
  app.innerHTML = `
    <div class="section__head">
      <div><h1>Poprzednie sesje</h1><p class="muted">Sesje są zapisane lokalnie. Możesz je wyeksportować do Markdown albo usunąć.</p></div>
      <a class="btn" href="#/">Nowa sesja</a>
    </div>
    ${sessionsTable(sessions)}`;
  bindSessionActions();
}

// ---------------- widok: rozmowa ----------------
async function viewSession(id) {
  setNav("");
  const data = await api(`/api/sessions/${id}`);
  if (data.session.status === "zakonczona") {
    location.hash = `#/raport/${id}`;
    return;
  }
  const sc = data.scenario;
  app.innerHTML = `
    <div class="session">
      <section class="card chat" aria-label="Rozmowa z kandydatem">
        <div class="chat__head">
          <span class="avatar" aria-hidden="true">${esc(initials(sc.candidate.name))}</span>
          <div class="grow">
            <strong>${esc(sc.candidate.name)}</strong> <span class="badge badge--ai">kandydat symulowany</span><br>
            <span class="small muted">${esc(sc.position)} · ${esc(sc.company)}</span>
          </div>
        </div>
        <div class="chat__log" id="log">
          <div class="chat__system">Rozmawiasz z symulowanym kandydatem (${status.mode === "ai" ? "AI" : "skrypt offline"}). Zadaj pierwsze pytanie — np. poproś o przedstawienie się.</div>
        </div>
        <form class="chat__form" id="ask-form">
          <label class="small muted" for="question">Twoje pytanie do kandydata</label>
          <textarea id="question" maxlength="1000" placeholder="Np. „Proszę opowiedzieć o sytuacji, w której…”" required></textarea>
          <div class="chat__actions">
            <div class="left">
              <button class="btn" type="submit" id="ask-btn">Zadaj pytanie</button>
              <span class="small muted" id="counter">0/1000 · Enter wysyła, Shift+Enter nowa linia</span>
            </div>
            <button class="btn btn--danger" type="button" id="finish-btn">Zakończ i pokaż raport</button>
          </div>
          <div id="form-error" class="small error" role="alert"></div>
        </form>
      </section>
      <aside class="coach" id="coach" aria-label="Panel coacha"></aside>
    </div>`;

  const log = document.getElementById("log");
  data.turns.forEach((t) => appendTurn(log, t));
  log.scrollTop = log.scrollHeight;
  const last = data.turns[data.turns.length - 1];
  renderCoach(sc, { lastTurn: last, progress: data.progress, starHint: data.star_hint, stats: data.stats, trapNotice: null });

  const form = document.getElementById("ask-form");
  const textarea = document.getElementById("question");
  const counter = document.getElementById("counter");
  const askBtn = document.getElementById("ask-btn");
  const finishBtn = document.getElementById("finish-btn");
  const formError = document.getElementById("form-error");
  textarea.focus();
  textarea.addEventListener("input", () => { counter.textContent = `${textarea.value.length}/1000 · Enter wysyła, Shift+Enter nowa linia`; });
  textarea.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); }
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const question = textarea.value.trim();
    if (!question) { formError.textContent = "Wpisz pytanie."; return; }
    formError.textContent = "";
    askBtn.disabled = finishBtn.disabled = true;
    const pending = appendPending(log, question);
    const typing = document.createElement("div");
    typing.className = "typing";
    typing.textContent = `${sc.candidate.name} odpowiada…`;
    log.appendChild(typing);
    log.scrollTop = log.scrollHeight;
    try {
      const res = await api(`/api/sessions/${id}/turns`, { method: "POST", body: JSON.stringify({ question }) });
      pending.remove();
      typing.remove();
      appendTurn(log, res.turn);
      textarea.value = "";
      counter.textContent = "0/1000 · Enter wysyła, Shift+Enter nowa linia";
      renderCoach(sc, { lastTurn: res.turn, progress: res.progress, starHint: res.star_hint, stats: res.stats, trapNotice: res.trap_notice });
    } catch (err) {
      pending.remove();
      typing.remove();
      formError.textContent = err.message;
    } finally {
      askBtn.disabled = finishBtn.disabled = false;
      log.scrollTop = log.scrollHeight;
      textarea.focus();
    }
  });

  finishBtn.addEventListener("click", async () => {
    finishBtn.disabled = askBtn.disabled = true;
    finishBtn.textContent = "Przygotowuję raport…";
    try {
      await api(`/api/sessions/${id}/finish`, { method: "POST" });
      location.hash = `#/raport/${id}`;
    } catch (err) {
      formError.textContent = err.message;
      finishBtn.disabled = askBtn.disabled = false;
      finishBtn.textContent = "Zakończ i pokaż raport";
    }
  });
}

function appendPending(log, question) {
  const el = document.createElement("div");
  el.className = "msg msg--me";
  el.innerHTML = `<div class="msg__body"><div class="bubble">${esc(question)}</div><div class="msg__meta">wysyłanie…</div></div>`;
  log.appendChild(el);
  return el;
}

function appendTurn(log, t) {
  const flagged = t.coach.flags.length > 0;
  const me = document.createElement("div");
  me.className = `msg msg--me${flagged ? " msg--flagged" : ""}`;
  me.innerHTML = `
    <div class="msg__body">
      <div class="bubble">${esc(t.question)}</div>
      <div class="msg__meta">
        <span class="badge ${GOOD_TYPES.has(t.coach.question_type) ? "badge--good" : "badge--warn"}">${esc(TYPE_LABELS[t.coach.question_type] || t.coach.question_type)}</span>
        ${t.coach.flags.map((f) => `<span class="badge badge--bad">⚠ ${esc(f.label)}</span>`).join("")}
      </div>
    </div>`;
  const them = document.createElement("div");
  them.className = "msg msg--them";
  them.innerHTML = `
    <div class="msg__body">
      <div class="bubble">${esc(t.answer)}</div>
      <div class="msg__meta">
        <span>emocja: ${esc(t.emotion_label)}</span>
        ${sourceBadge(t.candidate_source, "AI", "skrypt offline")}
        ${t.revealed_fact_ids.length ? `<span class="badge badge--good" title="Kandydat ujawnił nowe informacje">+${t.revealed_fact_ids.length} fakt</span>` : ""}
        ${t.candidate_note ? `<span class="badge badge--warn" title="${esc(t.candidate_note)}">uwaga</span>` : ""}
      </div>
    </div>`;
  log.append(me, them);
}

function renderCoach(sc, { lastTurn, progress, starHint, stats, trapNotice }) {
  const coach = document.getElementById("coach");
  let last = `<p class="muted small">Tu zobaczysz analizę każdego pytania: typ pytania, wskazówkę i ewentualne pytania niedozwolone.</p>`;
  if (lastTurn) {
    const c = lastTurn.coach;
    const flags = c.flags.map((f) => `
      <div class="alert alert--bad">
        <strong>⚠ Pytanie niedozwolone: ${esc(f.label)}</strong>
        ${esc(f.explanation)}
        <p class="legal">Podstawa: ${esc(f.legal_basis)}</p>
        <p class="suggestion"><b>Zamiast tego:</b> ${esc(f.suggestion)}</p>
        <p class="legal">Źródło: ${f.source === "ai" ? "klasyfikacja AI" : "reguły"}${f.trap_id ? " · dopytanie o wątek prywatny kandydata" : ""}</p>
      </div>`).join("");
    last = `
      <div class="coach__type">
        <span class="qtype">Pytanie ${esc(TYPE_LABELS[c.question_type] || c.question_type)}</span>
        ${c.flags.length
          ? `<span class="badge badge--bad">⚠ niedozwolone</span>`
          : `<span class="badge ${GOOD_TYPES.has(c.question_type) ? "badge--good" : "badge--warn"}">${GOOD_TYPES.has(c.question_type) ? "✓ dobra forma" : "! do poprawy"}</span>`}
        ${sourceBadge(c.type_source, "AI", "reguły")}
      </div>
      <p class="small">${esc(c.tip)}</p>
      ${flags || `<div class="alert alert--good small">✓ Nie wykryto pytań niedozwolonych.</div>`}
      ${c.injection_suspected ? `<div class="alert alert--warn small"><strong>Próba manipulacji?</strong>Pytanie wygląda na próbę wyjścia kandydata z roli. Kandydat zostaje w roli.</div>` : ""}
      ${c.ai_error ? `<p class="legal">AI niedostępne (${esc(c.ai_error)}) — analiza regułowa.</p>` : ""}`;
  }
  const talkShare = stats.trainee_share || 0;
  coach.innerHTML = `
    <section class="card">
      <div class="card__title"><h3>Coach: ostatnie pytanie</h3>${lastTurn ? `<span class="small muted">#${lastTurn.number}</span>` : ""}</div>
      ${last}
    </section>
    ${trapNotice ? `<section class="alert alert--warn"><strong>⚠ Pułapka</strong>${esc(trapNotice)}</section>` : ""}
    <section class="card">
      <h3>Podpowiedź STAR</h3>
      <p class="small">${esc(starHint)}</p>
      ${progress.map((p) => `
        <div class="star-row">
          <span class="star-row__name">${esc(p.name)}</span>
          <span class="pills" role="img" aria-label="${esc(p.name)}: ${STAR.filter((e) => p.elements[e]).map((e) => STAR_NAMES[e]).join(", ") || "brak"}">
            ${STAR.map((e) => `<span class="pill ${p.elements[e] ? "pill--on" : ""}" title="${STAR_NAMES[e]}${p.elements[e] ? " — wydobyto" : " — brak"}">${e}</span>`).join("")}
          </span>
        </div>`).join("")}
    </section>
    <section class="card">
      <h3>Statystyki na żywo</h3>
      <div class="stats-mini">
        <div><div class="stat-mini__label">Pytania</div><div class="stat-mini__value">${stats.questions}</div></div>
        <div><div class="stat-mini__label">Pytania otwarte</div><div class="stat-mini__value">${pct(stats.open_share)}</div></div>
        <div><div class="stat-mini__label">Wydobyte fakty</div><div class="stat-mini__value">${stats.facts_revealed}/${sc.facts_total}</div></div>
        <div><div class="stat-mini__label">Flagi prawne</div><div class="stat-mini__value">${stats.flags}</div></div>
      </div>
      <div class="meter" title="Udział Twoich słów w rozmowie: ${pct(talkShare)} (cel: najwyżej 30%)">
        <div class="stat-mini__label">Twój udział w mówieniu: <b>${pct(talkShare)}</b></div>
        <div class="meter__track"><div class="meter__fill" style="width:${Math.min(100, talkShare * 100)}%"></div><div class="meter__target" style="left:30%"></div></div>
        <div class="meter__legend"><span>0%</span><span>cel ≤ 30%</span><span>100%</span></div>
      </div>
    </section>`;
}

// ---------------- widok: raport ----------------
async function viewReport(id) {
  setNav("");
  const [session, report] = await Promise.all([api(`/api/sessions/${id}`), api(`/api/sessions/${id}/report`)]);
  const q = report.questions;
  const talk = report.talk;
  const typeMax = Math.max(1, ...Object.values(q.by_type));
  const talkGood = talk.trainee_share <= 0.3;
  app.innerHTML = `
    <div class="section__head">
      <div>
        <h1>Raport z sesji: ${esc(report.scenario_title)}</h1>
        <p class="muted small">Kandydat: ${esc(session.scenario.candidate.name)} · ${esc(fmtDate(session.session.created_at))} · ${report.turns} pytań</p>
      </div>
      <div class="actions">
        <a class="btn" href="/api/sessions/${esc(id)}/export.md">Pobierz Markdown</a>
        <a class="btn btn--ghost" href="#/">Nowa sesja</a>
      </div>
    </div>
    <div class="alert alert--info small">${esc(report.disclaimer)}</div>

    <section class="section kpis" aria-label="Najważniejsze wskaźniki">
      ${kpi("Pokrycie kompetencji", pct(report.coverage_share), `${report.coverage.filter((c) => c.covered).length} z ${report.coverage.length} kompetencji`)}
      ${kpi("Kompletność STAR (średnio)", pct(report.star_average), "S·T·A·R wydobyte z ujawnionych faktów")}
      ${kpi("Pytania otwarte", pct(q.open_share), `${q.open_count} z ${q.total} · cel ≥ 60%`)}
      ${kpi("Twój udział w mówieniu", pct(talk.trainee_share), `${talkGood ? "✓" : "!"} ${esc(talk.assessment)}`)}
      ${kpi("Incydenty prawne", String(report.incidents.length), report.incidents.length ? "⚠ pytania niedozwolone" : "✓ brak")}
    </section>

    <section class="section card">
      <h2>3 najważniejsze rekomendacje</h2>
      <ol class="recs">
        ${report.recommendations.map((r) => `<li><div><strong>${esc(r.title)}</strong><span class="small">${esc(r.detail)}</span></div></li>`).join("")}
      </ol>
    </section>

    <div class="section report-grid">
      <section class="card">
        <h2>Kompletność STAR per kompetencja</h2>
        <div class="table-wrap">
          <table class="star-table">
            <thead><tr><th>Kompetencja</th>${STAR.map((e) => `<th title="${STAR_NAMES[e]}">${e}</th>`).join("")}<th class="num">Razem</th></tr></thead>
            <tbody>
              ${report.coverage.map((c) => `<tr>
                <td>${esc(c.name)}</td>
                ${STAR.map((e) => c.elements_revealed.includes(e) ? `<td><span class="tick" title="${STAR_NAMES[e]}: wydobyto">✔</span></td>` : `<td><span class="dash" title="${STAR_NAMES[e]}: brak">—</span></td>`).join("")}
                <td class="num">${pct(c.star_completeness)}</td>
              </tr>`).join("")}
            </tbody>
          </table>
        </div>
        <p class="small muted">S — sytuacja, T — zadanie, A — działanie, R — rezultat. Liczone z faktów, które kandydat ujawnił w odpowiedziach.</p>
      </section>

      <section class="card">
        <h2>Statystyki pytań</h2>
        <div class="bars" role="list">
          ${Object.entries(q.by_type).map(([type, n]) => `
            <div class="bar-row ${n ? "" : "bar-row--zero"}" role="listitem" title="${esc(TYPE_LABELS[type])}: ${n}">
              <span>${esc(TYPE_LABELS[type] || type)}</span>
              <span class="bar-track"><span class="bar-fill" style="width:${(n / typeMax) * 100}%"></span></span>
              <span class="num">${n}</span>
            </div>`).join("")}
        </div>
        <p class="small muted" style="margin-top:10px">Proporcja mówienia: ${talk.trainee_words} słów rekrutera : ${talk.candidate_words} słów kandydata${talk.ratio != null ? ` (${talk.ratio.toFixed(2)})` : ""}.</p>
      </section>
    </div>

    <div class="section report-grid">
      <section class="card">
        <h2>Incydenty prawne</h2>
        ${report.incidents.length ? report.incidents.map((i) => `
          <div class="alert alert--bad">
            <strong>Tura ${i.turn}: ${esc(i.label)}</strong>
            „${esc(i.question)}”
            <p>${esc(i.explanation)}</p>
            <p class="legal">Podstawa: ${esc(i.legal_basis)}</p>
            <p class="suggestion"><b>Zamiast tego:</b> ${esc(i.suggestion)}</p>
          </div>`).join("") : `<div class="alert alert--good">✓ Nie padło żadne pytanie niedozwolone.</div>`}
      </section>
      <section class="card">
        <h2>Pułapki</h2>
        <p class="small muted">Wątki prywatne, które kandydat poruszył sam. Dobra praktyka: przyjąć informację neutralnie i nie dopytywać.</p>
        ${report.traps.length ? report.traps.map((t) => {
          const cls = t.outcome === "wejscie" ? "alert--bad" : t.outcome === "uniknieta" ? "alert--good" : "alert--info";
          const label = t.outcome === "wejscie" ? `⚠ Wejście w pułapkę (tura ${t.entered_turn})` : t.outcome === "uniknieta" ? "✓ Pułapka uniknięta" : "Nie wystąpiła w tej rozmowie";
          return `<div class="alert ${cls}"><strong>${esc(t.label)} — ${label}</strong>${t.mentioned_turn ? `Wątek pojawił się w turze ${t.mentioned_turn}. ` : ""}${esc(t.note)}</div>`;
        }).join("") : `<p class="muted">Ten scenariusz nie ma pułapek.</p>`}
      </section>
    </div>

    <section class="section card">
      <div class="card__title"><h2>Uwagi jakościowe</h2>${sourceBadge(report.notes_source, "AI", "reguły offline")}</div>
      ${report.notes.length ? report.notes.map((n) => `
        <div class="note">
          <span class="badge ${n.kind === "mocna_strona" ? "badge--good" : "badge--warn"}">${n.kind === "mocna_strona" ? "✓ mocna strona" : "! do poprawy"}</span>
          <p style="margin-top:6px">${esc(n.observation)}</p>
          <blockquote class="quote">${esc(n.quote)}</blockquote>
          <p class="small"><b>Wskazówka:</b> ${esc(n.suggestion)}</p>
        </div>`).join("") : `<p class="muted">Brak uwag — rozmowa była zbyt krótka.</p>`}
      ${report.rejected_notes ? `<p class="legal">Odrzucono ${report.rejected_notes} uwag AI, których cytatów nie znaleziono w transkrypcie.</p>` : ""}
      ${report.ai_error ? `<p class="legal">AI niedostępne (${esc(report.ai_error)}) — pokazano uwagi regułowe.</p>` : ""}
    </section>

    <section class="section card">
      <details>
        <summary>Transkrypt rozmowy (${session.turns.length} tur)</summary>
        <div style="margin-top:10px">
          ${session.turns.map((t) => `
            <div class="transcript-line">
              <div><b>[${t.number}] Ty</b> <span class="badge">${esc(TYPE_LABELS[t.coach.question_type])}</span> ${t.coach.flags.map((f) => `<span class="badge badge--bad">⚠ ${esc(f.label)}</span>`).join("")}<br>${esc(t.question)}</div>
              <div style="margin-top:4px"><b>${esc(session.scenario.candidate.name)}</b> <span class="small muted">(${esc(t.emotion_label)})</span><br>${esc(t.answer)}</div>
            </div>`).join("")}
        </div>
      </details>
    </section>`;
}

function kpi(label, value, note) {
  return `<div class="kpi"><div class="kpi__label">${esc(label)}</div><div class="kpi__value">${esc(value)}</div><div class="kpi__note">${note}</div></div>`;
}

// ---------------- routing ----------------
async function route() {
  const hash = location.hash || "#/";
  const parts = hash.slice(2).split("/");
  try {
    if (parts[0] === "sesja" && parts[1]) await viewSession(parts[1]);
    else if (parts[0] === "raport" && parts[1]) await viewReport(parts[1]);
    else if (parts[0] === "sesje") await viewSessions();
    else await viewHome();
    window.scrollTo(0, 0);
  } catch (err) {
    showError(err);
  }
}

async function init() {
  try {
    status = await api("/api/status");
  } catch { /* zostaje offline */ }
  const badge = document.getElementById("mode-badge");
  badge.textContent = status.mode === "ai" ? "Tryb AI" : "Tryb offline";
  badge.className = `badge ${status.mode === "ai" ? "badge--ai" : "badge--muted"}`;
  document.getElementById("ai-notice-mode").textContent =
    status.mode === "ai" ? "odgrywa go model sztucznej inteligencji (AI)" : "w trybie offline odgrywa go skrypt, a w trybie AI — model sztucznej inteligencji";
  window.addEventListener("hashchange", route);
  route();
}

init();

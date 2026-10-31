"use strict";

// ---------- narzędzia ----------
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "html") el.innerHTML = v; // tylko dla stałych SVG z kodu
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const store = {
  get(key, session = false) {
    try { return (session ? sessionStorage : localStorage).getItem(key); } catch { return null; }
  },
  set(key, value, session = false) {
    try { (session ? sessionStorage : localStorage).setItem(key, value); } catch { /* tryb prywatny */ }
  },
};

const state = {
  session: store.get("asystent.session"),
  hrToken: store.get("asystent.hrToken", true) || "",
  hrContact: "hr@zefirion.example",
};

async function api(path, { method = "GET", body, hr = false, headers = {} } = {}) {
  const opts = { method, headers: { ...headers } };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  if (hr && state.hrToken) opts.headers["X-HR-Token"] = state.hrToken;
  const res = await fetch(path, opts);
  let data = null;
  try { data = await res.json(); } catch { /* pusta odpowiedź */ }
  if (!res.ok) {
    if (res.status === 401 && hr) $("#token-form").classList.remove("hidden");
    let msg = (data && data.detail) || `Błąd ${res.status}`;
    if (data && data.bledy) msg += ": " + data.bledy.map((b) => `${b.pole} — ${b.komunikat}`).join("; ");
    throw new Error(msg);
  }
  return data;
}

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString("pl-PL", { dateStyle: "short", timeStyle: "short" }) : "—");

const ICON_UP = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 10v11H4V10h3zm0 0 4-7a2 2 0 0 1 2 2v4h5.5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.3 21H7"/></svg>';
const ICON_DOWN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 14V3h3v11h-3zm0 0-4 7a2 2 0 0 1-2-2v-4H5.5a2 2 0 0 1-2-2.3l1.2-7A2 2 0 0 1 6.7 3H17"/></svg>';

// ---------- widoki ----------
function showView(name) {
  $$(".tab").forEach((t) => {
    const on = t.dataset.view === name;
    t.classList.toggle("active", on);
    t.setAttribute("aria-selected", on);
  });
  $$(".view").forEach((v) => v.classList.toggle("active", v.id === `view-${name}`));
  if (name === "hr") showHr(currentHr);
}

let currentHr = "docs";
function showHr(name) {
  currentHr = name;
  $$(".subtab").forEach((t) => t.classList.toggle("active", t.dataset.hr === name));
  $$(".hr-section").forEach((s) => s.classList.toggle("active", s.id === `hr-${name}`));
  ({ docs: loadDocs, tickets: loadTickets, gaps: loadGaps, stats: loadStats })[name]();
}

// ---------- sesja ----------
async function ensureSession(force = false) {
  if (!state.session || force) {
    const s = await api("/api/sessions", { method: "POST" });
    state.session = s.session_token;
    store.set("asystent.session", state.session);
  }
  $("#session-id").textContent = state.session.slice(0, 8) + "…";
}

// ---------- czat ----------
const SUGGESTIONS = [
  "Ile mam dni urlopu?",
  "Jak zgłosić L4?",
  "Ile dni pracy zdalnej mi przysługuje?",
  "Kiedy wypłata?",
  "Jak są płatne nadgodziny?",
  "Gdzie zgłosić mobbing?",
];

function renderSuggestions() {
  const box = $("#suggestions");
  SUGGESTIONS.forEach((q) => box.append(h("button", { class: "chip", type: "button", onclick: () => ask(q) }, q)));
}

function scrollMessages() {
  const m = $("#messages");
  m.scrollTop = m.scrollHeight;
}

async function ask(question) {
  question = question.trim();
  if (!question) return;
  const messages = $("#messages");
  messages.append(h("div", { class: "msg user" }, question));
  const typing = h("div", { class: "msg bot typing" }, "Szukam w dokumentach…");
  messages.append(typing);
  scrollMessages();
  $("#ask-btn").disabled = true;
  try {
    const data = await api("/api/ask", { method: "POST", body: { question, session_token: state.session } });
    if (data.session_token !== state.session) {
      state.session = data.session_token;
      store.set("asystent.session", state.session);
      $("#session-id").textContent = state.session.slice(0, 8) + "…";
    }
    state.hrContact = data.hr_contact || state.hrContact;
    typing.replaceWith(renderAnswer(data));
  } catch (err) {
    typing.replaceWith(h("div", { class: "msg bot" }, h("div", { class: "notice warn" }, err.message)));
  } finally {
    $("#ask-btn").disabled = false;
    scrollMessages();
  }
}

const CONF_CLASS = { wysoka: "ok", "średnia": "warn", niska: "danger" };

function renderAnswer(d) {
  const meta = h("div", { class: "meta" },
    d.source === "ai"
      ? h("span", { class: "badge ai", title: "Odpowiedź modelu AI zweryfikowana z cytatami" }, "AI")
      : h("span", { class: "badge offline", title: "Tryb offline — wyciąg z dokumentów" }, d.is_extract ? "Offline · wyciąg" : "Offline"));
  if (d.status === "answered") {
    meta.append(h("span", { class: `badge ${CONF_CLASS[d.confidence]}` }, `Pewność: ${d.confidence}`));
  } else if (d.status === "no_basis") {
    meta.append(h("span", { class: "badge warn" }, "Brak podstawy w dokumentach"));
  } else {
    meta.append(h("span", { class: "badge info" }, "Temat wrażliwy"));
  }
  (d.category_labels || []).forEach((c) => meta.append(h("span", { class: "badge" }, c)));
  if (d.injection_suspected) meta.append(h("span", { class: "badge danger" }, "Polecenia w pytaniu zignorowane"));

  const box = h("div", { class: "msg bot" }, meta, h("div", { class: "answer" }, d.answer));
  (d.notices || []).forEach((n) => box.append(h("div", { class: "notice" }, n)));
  if (d.needs_hr) {
    box.append(h("div", { class: "notice hr" },
      `Ta sprawa wymaga kontaktu z HR — przekaż pytanie przyciskiem poniżej albo napisz na ${d.hr_contact || state.hrContact}.`));
  }
  if (d.citations && d.citations.length) {
    const list = h("div", { class: "citations" });
    d.citations.forEach((c, i) => list.append(renderCitation(c, i)));
    box.append(list);
  }
  box.append(renderActions(d));
  return box;
}

function renderCitation(c, i) {
  const fragment = h("div", { class: "fragment" });
  highlight(fragment, c.fragment, c.quote);
  return h("details", { class: "cite", open: i === 0 ? true : null },
    h("summary", {}, `${c.title}, ${c.section} `, h("span", { class: "muted" }, `(wersja ${c.version}, obowiązuje od ${c.effective_from})`)),
    h("div", { class: "path" }, c.section_path.join(" › ")),
    h("blockquote", {}, `„${c.quote}”`),
    h("details", {}, h("summary", { class: "small muted" }, "Pokaż pełny fragment"), fragment));
}

function highlight(target, text, quote) {
  const words = quote.split(/\s+/).filter(Boolean).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const match = words.length ? new RegExp(words.join("\\s+")).exec(text) : null;
  if (!match) { target.textContent = text; return; }
  target.append(text.slice(0, match.index), h("mark", {}, match[0]), text.slice(match.index + match[0].length));
}

function renderActions(d) {
  const up = h("button", { class: "icon-btn", type: "button", title: "Pomocna odpowiedź", "aria-label": "Pomocna odpowiedź", html: ICON_UP });
  const down = h("button", { class: "icon-btn", type: "button", title: "Niepomocna odpowiedź", "aria-label": "Niepomocna odpowiedź", html: ICON_DOWN });
  const status = h("span", { class: "small muted" });
  const vote = async (value, btn) => {
    try {
      await api(`/api/interactions/${d.interaction_id}/feedback`, { method: "POST", body: { session_token: state.session, vote: value } });
      up.classList.toggle("selected", btn === up);
      down.classList.toggle("selected", btn === down);
      status.textContent = "Dziękuję za ocenę.";
    } catch (err) { status.textContent = err.message; }
  };
  up.addEventListener("click", () => vote("up", up));
  down.addEventListener("click", () => vote("down", down));
  const escBtn = h("button", { class: d.needs_hr ? "btn small primary" : "btn small", type: "button" }, "Przekaż do HR");
  const wrap = h("div", {}, h("div", { class: "actions" }, up, down, escBtn, status));
  escBtn.addEventListener("click", () => {
    if (wrap.querySelector(".esc-form")) return;
    wrap.append(renderEscalationForm(d, escBtn));
  });
  return wrap;
}

function renderEscalationForm(d, escBtn) {
  const note = h("textarea", { rows: 2, maxlength: 1000, placeholder: "Dodatkowy kontekst (opcjonalnie) — bez danych o zdrowiu" });
  const contact = h("input", { maxlength: 200, placeholder: "Kontakt do odpowiedzi poza czatem (opcjonalnie)" });
  const result = h("p", { class: "small", role: "status" });
  const form = h("form", { class: "esc-form" },
    h("p", { class: "small muted" }, "HR zobaczy Twoje pytanie (z zamaskowanymi danymi osobowymi) i kontekst odpowiedzi. Odpowiedź pojawi się w „Moich zgłoszeniach”."),
    note, h("div", { style: "height:6px" }), contact, h("div", { style: "height:8px" }),
    h("button", { class: "btn small primary", type: "submit" }, "Wyślij do HR"), result);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await api("/api/escalations", {
        method: "POST",
        body: { session_token: state.session, interaction_id: d.interaction_id, note: note.value || null, contact: contact.value || null },
      });
      form.replaceChildren(h("p", { class: "small" }, "Przekazano do HR. Odpowiedź zobaczysz w panelu „Moje zgłoszenia do HR”."));
      escBtn.disabled = true;
      loadMine();
    } catch (err) { result.textContent = err.message; }
  });
  return form;
}

const STATUS_LABEL = { nowe: "nowe", odpowiedziane: "odpowiedziane", "zamknięte": "zamknięte" };
const STATUS_CLASS = { nowe: "warn", odpowiedziane: "ok", "zamknięte": "" };

async function loadMine() {
  const box = $("#my-escalations");
  try {
    const items = await api("/api/me/escalations", { headers: { "X-Session-Token": state.session || "" } });
    if (!items.length) { box.textContent = "Brak zgłoszeń."; box.className = "small muted"; return; }
    box.className = "small";
    box.replaceChildren(...items.map((e) => h("div", { class: "mine-item" },
      h("div", {}, h("span", { class: `badge ${STATUS_CLASS[e.status]}` }, STATUS_LABEL[e.status]), " ", h("span", { class: "muted" }, fmtDate(e.created_at))),
      h("div", {}, e.question),
      e.hr_reply ? h("div", { class: "reply" }, h("strong", {}, "HR: "), e.hr_reply) : null)));
  } catch (err) { box.textContent = err.message; }
}

// ---------- panel HR: dokumenty ----------
const DOC_STATUS_CLASS = { aktualna: "ok", zaplanowana: "info", archiwalna: "", wycofana: "danger" };

async function loadDocs() {
  const body = $("#docs-body");
  try {
    const docs = await api("/api/hr/documents", { hr: true });
    $("#token-form").classList.add("hidden");
    body.replaceChildren(...docs.flatMap((d) => {
      const preview = h("tr", { class: "hidden" }, h("td", { colspan: 6 }, h("div", { class: "chunk-list" }, "Ładowanie…")));
      const toggle = h("button", { class: "link", type: "button" }, "Fragmenty");
      toggle.addEventListener("click", async () => {
        preview.classList.toggle("hidden");
        if (preview.dataset.loaded) return;
        const detail = await api(`/api/hr/documents/${d.id}`, { hr: true });
        preview.dataset.loaded = "1";
        preview.querySelector(".chunk-list").replaceChildren(...detail.chunks.map((c) =>
          h("div", { class: "chunk" }, h("div", { class: "path" }, c.section_path.join(" › ")), h("div", { class: "fragment" }, c.text))));
      });
      const withdraw = h("button", { class: d.withdrawn ? "link" : "link btn danger", type: "button" }, d.withdrawn ? "Przywróć" : "Wycofaj");
      withdraw.addEventListener("click", async () => {
        await api(`/api/hr/documents/${d.id}/withdraw`, { method: "POST", hr: true, body: { withdrawn: !d.withdrawn } });
        loadDocs();
      });
      const row = h("tr", { class: d.status === "archiwalna" || d.status === "wycofana" ? "archival" : "" },
        h("td", {}, h("strong", {}, d.title), h("div", { class: "small muted" }, d.doc_key, " · ", d.filename),
          d.injection_flag ? h("div", {}, h("span", { class: "badge danger", title: "Treść zawiera polecenia skierowane do modelu — sprawdź dokument. Asystent traktuje je jako dane." }, "Podejrzane polecenia w treści")) : null),
        h("td", {}, d.version),
        h("td", {}, d.effective_from),
        h("td", {}, h("span", { class: `badge ${DOC_STATUS_CLASS[d.status]}` }, d.status)),
        h("td", {}, d.chunk_count),
        h("td", {}, toggle, " ", withdraw));
      return [row, preview];
    }));
  } catch (err) {
    body.replaceChildren(h("tr", {}, h("td", { colspan: 6, class: "empty" }, err.message)));
  }
}

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

async function uploadDocument(e) {
  e.preventDefault();
  const file = $("#up-file").files[0];
  const out = $("#upload-result");
  if (!file) return;
  out.textContent = "Wgrywanie…";
  try {
    const doc = await api("/api/hr/documents", {
      method: "POST", hr: true,
      body: {
        filename: file.name,
        content_base64: toBase64(await file.arrayBuffer()),
        title: $("#up-title").value || null,
        doc_key: $("#up-key").value || null,
        version: $("#up-version").value || null,
        effective_from: $("#up-date").value || null,
      },
    });
    out.textContent = `Dodano „${doc.title}” w wersji ${doc.version} (${doc.chunk_count} fragmentów, status: ${doc.status}).`;
    e.target.reset();
    loadDocs();
  } catch (err) { out.textContent = err.message; }
}

async function searchTest(e) {
  e.preventDefault();
  const out = $("#search-results");
  try {
    const hits = await api(`/api/hr/search?q=${encodeURIComponent($("#search-q").value)}`, { hr: true });
    out.replaceChildren(...(hits.length ? hits.map((x) => h("div", { class: "hit" },
      h("div", {}, h("strong", {}, x.path)),
      h("div", { class: "muted" }, `BM25: ${x.score.toFixed(2)} · pokrycie pojęć: ${Math.round(x.coverage * 100)}%`))) : [h("p", { class: "muted" }, "Brak wyników.")]));
  } catch (err) { out.textContent = err.message; }
}

// ---------- panel HR: zgłoszenia ----------
async function loadTickets() {
  const box = $("#tickets");
  try {
    const status = $("#ticket-filter").value;
    const items = await api(`/api/hr/escalations${status ? `?status=${encodeURIComponent(status)}` : ""}`, { hr: true });
    updateOpenCount();
    if (!items.length) { box.replaceChildren(h("div", { class: "card empty" }, "Brak zgłoszeń w tym widoku.")); return; }
    box.replaceChildren(...items.map(renderTicket));
  } catch (err) { box.replaceChildren(h("div", { class: "card empty" }, err.message)); }
}

function renderTicket(e) {
  const ctx = e.context || {};
  const reply = h("textarea", { rows: 3, maxlength: 4000, placeholder: "Odpowiedź dla pracownika" });
  const out = h("span", { class: "small", role: "status" });
  const send = (close) => async () => {
    if (!reply.value.trim()) { out.textContent = "Wpisz odpowiedź."; return; }
    try {
      await api(`/api/hr/escalations/${e.id}/reply`, { method: "POST", hr: true, body: { reply: reply.value, close } });
      loadTickets();
    } catch (err) { out.textContent = err.message; }
  };
  const closeOnly = async () => {
    await api(`/api/hr/escalations/${e.id}/close`, { method: "POST", hr: true });
    loadTickets();
  };
  const ctxBits = [];
  if (ctx.status) ctxBits.push(`wynik asystenta: ${ { answered: "odpowiedź", no_basis: "brak podstawy", sensitive: "temat wrażliwy" }[ctx.status] || ctx.status }`);
  if (ctx.source) ctxBits.push(`źródło: ${ctx.source}`);
  if (ctx.confidence) ctxBits.push(`pewność: ${ctx.confidence}`);
  if (ctx.categories && ctx.categories.length) ctxBits.push(`kategorie: ${ctx.categories.join(", ")}`);
  return h("div", { class: "card ticket" },
    h("div", { class: "card-head" },
      h("div", {}, h("span", { class: `badge ${STATUS_CLASS[e.status]}` }, STATUS_LABEL[e.status]), " ",
        h("span", { class: "small muted" }, `#${e.id} · ${fmtDate(e.created_at)}`))),
    h("div", { class: "q" }, e.question),
    ctxBits.length ? h("div", { class: "ctx" }, ctxBits.join(" · ")) : null,
    ctx.cited && ctx.cited.length ? h("div", { class: "ctx" }, "Cytowane: ", ctx.cited.join("; ")) : null,
    e.note ? h("div", { class: "ctx" }, h("strong", {}, "Notatka: "), e.note) : null,
    e.contact ? h("div", { class: "ctx" }, h("strong", {}, "Kontakt: "), e.contact) : null,
    e.hr_reply ? h("div", { class: "reply small" }, h("strong", {}, "Odpowiedź HR: "), e.hr_reply) : null,
    e.status === "zamknięte" ? null : h("div", {}, reply,
      h("div", { class: "actions" },
        h("button", { class: "btn small primary", type: "button", onclick: send(false) }, "Odpowiedz"),
        h("button", { class: "btn small", type: "button", onclick: send(true) }, "Odpowiedz i zamknij"),
        h("button", { class: "btn small", type: "button", onclick: closeOnly }, "Zamknij bez odpowiedzi"), out)));
}

async function updateOpenCount() {
  try {
    const open = await api("/api/hr/escalations?status=nowe", { hr: true });
    $("#open-count").textContent = open.length ? String(open.length) : "";
  } catch { /* brak dostępu */ }
}

// ---------- panel HR: luki w wiedzy ----------
async function loadGaps() {
  const box = $("#gaps");
  try {
    const r = await api(`/api/hr/gaps?days=${$("#gaps-days").value}`, { hr: true });
    const summary = h("p", { class: "small muted" }, `Pytań w okresie: ${r.total_questions}, w tym bez dobrej odpowiedzi: ${r.gap_questions}.`);
    if (!r.groups.length) { box.replaceChildren(summary, h("div", { class: "card empty" }, "Brak luk w wiedzy w wybranym okresie.")); return; }
    box.replaceChildren(summary, ...r.groups.map((g) => {
      const chips = [];
      if (g.no_basis) chips.push(h("span", { class: "badge warn" }, `bez podstawy: ${g.no_basis}`));
      if (g.low_confidence) chips.push(h("span", { class: "badge" }, `niska pewność: ${g.low_confidence}`));
      if (g.thumbs_down) chips.push(h("span", { class: "badge danger" }, `oceny negatywne: ${g.thumbs_down}`));
      if (g.escalated) chips.push(h("span", { class: "badge info" }, `przekazane do HR: ${g.escalated}`));
      return h("div", { class: "card gap" },
        h("div", {}, h("div", { class: "num" }, g.count), h("div", { class: "small muted" }, g.count === 1 ? "pytanie" : "pytań")),
        h("div", {},
          h("h3", {}, `„${g.keyword}”`),
          h("div", { class: "meta" }, ...chips),
          h("ul", {}, ...g.examples.map((q) => h("li", {}, q))),
          h("div", { class: "suggest" }, g.suggestion)));
    }));
  } catch (err) { box.replaceChildren(h("div", { class: "card empty" }, err.message)); }
}

// ---------- panel HR: statystyki ----------
const tooltip = $("#tooltip");
function attachTooltip(el, text) {
  el.addEventListener("mousemove", (e) => {
    tooltip.textContent = text;
    tooltip.hidden = false;
    tooltip.style.left = `${e.clientX + 12}px`;
    tooltip.style.top = `${e.clientY - 28}px`;
  });
  el.addEventListener("mouseleave", () => { tooltip.hidden = true; });
}

function kpi(label, value, sub) {
  return h("div", { class: "kpi" }, h("div", { class: "label" }, label), h("div", { class: "value" }, value), sub ? h("div", { class: "sub" }, sub) : null);
}

function hbars(target, rows) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  target.replaceChildren(...(rows.length ? rows.map((r) => {
    const row = h("div", { class: "hbar" },
      h("span", { class: "name", title: r.label }, r.label),
      h("div", { class: "track" }, h("div", { class: "fill", style: `width:${(r.value / max) * 100}%` })),
      h("span", { class: "n" }, r.value));
    attachTooltip(row, `${r.label}: ${r.value}`);
    return row;
  }) : [h("p", { class: "small muted" }, "Brak danych.")]));
}

async function loadStats() {
  try {
    const [s, ret] = await Promise.all([api("/api/hr/stats", { hr: true }), api("/api/hr/retention", { hr: true })]);
    const open = (s.escalations["nowe"] || 0) + (s.escalations["odpowiedziane"] || 0);
    $("#kpis").replaceChildren(
      kpi("Pytania", s.total_questions, "od początku retencji"),
      kpi("Odpowiedziane", `${Math.round(s.answered_rate * 100)}%`, "z cytatem z dokumentów"),
      kpi("Bez podstawy", s.by_status.no_basis || 0, "przekierowane do HR"),
      kpi("Zgłoszenia otwarte", open, `zamknięte: ${s.escalations["zamknięte"] || 0}`),
      kpi("Oceny", `${s.feedback.up} / ${s.feedback.down}`, "pomocne / niepomocne"),
      kpi("Dokumenty aktualne", s.documents_current, `wszystkich wersji: ${s.documents_total}`));

    const max = Math.max(1, ...s.per_day.map((d) => d.count));
    const cols = $("#per-day");
    cols.replaceChildren(...s.per_day.map((d) => {
      const col = h("div", { class: "col" },
        d.count ? h("span", { class: "val" }, d.count) : null,
        h("div", { class: "bar", style: `height:${(d.count / max) * 85}%` }));
      attachTooltip(col, `${d.date}: ${d.count} pyt.`);
      return col;
    }));
    const labels = h("div", { class: "col-labels" }, ...s.per_day.map((d, i) => h("span", {}, i % 2 === 1 ? "" : d.date.slice(8, 10) + "." + d.date.slice(5, 7))));
    cols.nextElementSibling && cols.nextElementSibling.classList.contains("col-labels") ? cols.nextElementSibling.replaceWith(labels) : cols.after(labels);

    const statusNames = { answered: "Odpowiedź z cytatem", no_basis: "Brak podstawy", sensitive: "Temat wrażliwy" };
    hbars($("#by-status"), Object.entries(statusNames).map(([k, label]) => ({ label, value: s.by_status[k] || 0 }))
      .concat([{ label: "Tryb AI", value: s.by_source.ai || 0 }, { label: "Tryb offline", value: s.by_source.offline || 0 }]));
    hbars($("#top-docs"), s.top_documents.map((d) => ({ label: d.title, value: d.count })));

    $("#ret-days").textContent = ret.days;
    $("#ret-last").textContent = fmtDate(ret.last_purge);
    $("#purge-days").placeholder = ret.days;
  } catch (err) { $("#kpis").replaceChildren(h("div", { class: "card empty" }, err.message)); }
}

async function purge(e) {
  e.preventDefault();
  const days = parseInt($("#purge-days").value, 10);
  const out = $("#purge-result");
  try {
    const r = await api("/api/hr/retention/purge", { method: "POST", hr: true, body: { days: Number.isFinite(days) ? days : null } });
    out.textContent = `Usunięto: pytania ${r.interactions}, zgłoszenia ${r.escalations} (starsze niż ${r.days} dni).`;
    loadStats();
  } catch (err) { out.textContent = err.message; }
}

// ---------- start ----------
async function init() {
  $$(".tab").forEach((t) => t.addEventListener("click", () => showView(t.dataset.view)));
  $$(".subtab").forEach((t) => t.addEventListener("click", () => showHr(t.dataset.hr)));
  $("#ask-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = $("#question").value;
    $("#question").value = "";
    ask(q);
  });
  $("#question").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); $("#ask-form").requestSubmit(); }
  });
  $("#new-session").addEventListener("click", async () => {
    await ensureSession(true);
    loadMine();
  });
  $("#refresh-mine").addEventListener("click", loadMine);
  $("#upload-form").addEventListener("submit", uploadDocument);
  $("#search-form").addEventListener("submit", searchTest);
  $("#ticket-filter").addEventListener("change", loadTickets);
  $("#gaps-days").addEventListener("change", loadGaps);
  $("#purge-form").addEventListener("submit", purge);
  $("#token-form").addEventListener("submit", (e) => {
    e.preventDefault();
    state.hrToken = $("#hr-token").value;
    store.set("asystent.hrToken", state.hrToken, true);
    showHr(currentHr);
  });
  renderSuggestions();

  try {
    const health = await api("/api/health");
    const badge = $("#mode-badge");
    badge.textContent = health.mode === "ai" ? "Tryb AI" : "Tryb offline";
    badge.className = `badge ${health.mode === "ai" ? "ai" : "offline"}`;
    badge.title = health.mode === "ai"
      ? "Odpowiedzi generuje model AI na podstawie fragmentów; każdy cytat jest weryfikowany."
      : "Brak klucza API lub HR_AI_OFFLINE=1 — odpowiedzi są wyciągami z dokumentów.";
    $("#retention-days").textContent = health.retention_days;
    state.hrContact = health.hr_contact;
    if (health.hr_token_required && !state.hrToken) $("#token-form").classList.remove("hidden");
    await ensureSession();
    loadMine();
    if (location.hash === "#hr") showView("hr");
  } catch (err) {
    $("#messages").append(h("div", { class: "msg bot" }, h("div", { class: "notice warn" }, `Nie udało się połączyć z serwerem: ${err.message}`)));
  }
}

init();

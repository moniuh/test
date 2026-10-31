"use strict";

const SEV_LABEL = { error: "błąd", warning: "ostrzeżenie", suggestion: "sugestia" };
const SEV_ORDER = { error: 0, warning: 1, suggestion: 2 };
const KIND_LABEL = { replaced: "zamiana", removed: "usunięcie", added: "uzupełnienie", manual: "do ręcznej poprawy" };
const VERDICT_COLOR = { ok: "var(--ok)", minor: "var(--suggestion)", needs_work: "var(--warning)", high_risk: "var(--error)" };
const VERDICT_LABEL = { ok: "zgodne", minor: "drobne poprawki", needs_work: "wymaga poprawy", high_risk: "wysokie ryzyko" };

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

const state = { aiEnabled: false, lastText: "", lastAudit: null, rewrite: null, filter: "all", cycle: {} };

// Polska odmiana liczebników: 1 błąd, 2 błędy, 5 błędów.
function plural(n, one, few, many) {
  const abs = Math.abs(n);
  if (abs === 1) return `${n} ${one}`;
  const d = abs % 10, h = abs % 100;
  return `${n} ${d >= 2 && d <= 4 && (h < 12 || h > 14) ? few : many}`;
}

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

async function api(path, options = {}) {
  const init = { headers: { "Content-Type": "application/json" }, ...options };
  let response;
  try {
    response = await fetch(path, init);
  } catch (err) {
    throw new Error("Brak połączenia z serwerem.");
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const details = Array.isArray(data.errors) ? data.errors.map((e) => `${e.pole}: ${e.komunikat}`).join("; ") : "";
    throw new Error([data.detail || `Błąd ${response.status}`, details].filter(Boolean).join(" — "));
  }
  return data;
}

function setBusy(button, busy, label) {
  if (!button.dataset.label) button.dataset.label = button.textContent;
  button.disabled = busy;
  button.textContent = busy ? label : button.dataset.label;
}

function showError(el, message) {
  el.textContent = message || "";
  el.hidden = !message;
}

/* ------------------------------------------------------------------ zakładki */
function switchTab(name) {
  $$(".tab").forEach((t) => t.setAttribute("aria-selected", String(t.dataset.tab === name)));
  $$(".tab-panel").forEach((p) => (p.hidden = p.id !== `tab-${name}`));
  if (name === "rules" && !$("#rules-list").childElementCount) loadRules();
}

/* ------------------------------------------------------------------ start */
async function loadHealth() {
  const badge = $("#mode-badge");
  try {
    const health = await api("/api/health");
    state.aiEnabled = health.ai_enabled;
    badge.className = "badge " + (health.ai_enabled ? "badge-ai" : "badge-offline");
    badge.textContent = health.ai_enabled ? "Tryb AI włączony" : "Tryb offline (bez klucza API)";
    badge.title = `${plural(health.rules, "reguła", "reguły", "reguł")} w silniku`;
    $("#formula-text").textContent = health.formula;
    const toggle = $("#use-ai");
    if (!health.ai_enabled) {
      toggle.checked = false;
      toggle.disabled = true;
      $("#ai-toggle").classList.add("disabled");
      $("#ai-toggle").title = "AI niedostępne — ustaw ANTHROPIC_API_KEY. Działa silnik reguł.";
    }
  } catch (err) {
    badge.textContent = "serwer niedostępny";
  }
}

async function loadExamples() {
  const box = $("#examples");
  try {
    const list = await api("/api/examples");
    list.forEach((ex) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "example-btn";
      btn.title = ex.description;
      btn.innerHTML = `<span class="kind kind-${esc(ex.kind.replace(/\s+/g, "-"))}">${esc(ex.kind)}</span>${esc(ex.title)}`;
      btn.addEventListener("click", () => loadExample(ex.id));
      box.appendChild(btn);
    });
  } catch (err) {
    box.insertAdjacentHTML("beforeend", `<span class="error">${esc(err.message)}</span>`);
  }
}

async function loadExample(id) {
  const ex = await api(`/api/examples/${encodeURIComponent(id)}`);
  $("#ad-text").value = ex.text;
  updateCount();
  runAudit();
}

function updateCount() {
  const n = Array.from($("#ad-text").value).length;
  $("#char-count").textContent = plural(n, "znak", "znaki", "znaków");
}

/* ------------------------------------------------------------------ audyt */
async function runAudit() {
  const text = $("#ad-text").value;
  const button = $("#audit-btn");
  showError($("#audit-error"), "");
  if (!text.trim()) {
    showError($("#audit-error"), "Wklej treść ogłoszenia albo wybierz przykład.");
    return;
  }
  setBusy(button, true, "Sprawdzam…");
  try {
    const result = await api("/api/audit", { method: "POST", body: JSON.stringify({ text, use_ai: $("#use-ai").checked }) });
    state.lastText = text;
    state.lastAudit = result;
    state.cycle = {};
    renderScore(result);
    renderHighlights(text, result.findings);
    renderFindings(result);
    $("#results").hidden = false;
    $("#rewrite-card").hidden = false;
    $("#rewrite-body").hidden = true;
    $("#rewrite-source").innerHTML = "";
    $("#injection-banner").hidden = !result.injection_suspected;
  } catch (err) {
    showError($("#audit-error"), err.message);
  } finally {
    setBusy(button, false);
  }
}

function sourceBadge(source) {
  return source === "ai"
    ? '<span class="badge badge-ai">źródło: AI + reguły</span>'
    : '<span class="badge badge-offline">źródło: offline (reguły)</span>';
}

function ring(score, verdict) {
  const r = 52, c = 2 * Math.PI * r, offset = c * (1 - score / 100);
  return `<div class="ring" role="img" aria-label="Wynik ${score} na 100">
    <svg width="120" height="120" viewBox="0 0 120 120">
      <circle cx="60" cy="60" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="12"></circle>
      <circle cx="60" cy="60" r="${r}" fill="none" stroke="${VERDICT_COLOR[verdict]}" stroke-width="12"
        stroke-linecap="round" stroke-dasharray="${c.toFixed(2)}" stroke-dashoffset="${offset.toFixed(2)}"></circle>
    </svg>
    <div class="value"><div><strong>${score}</strong><span>na 100</span></div></div>
  </div>`;
}

function renderScore(result) {
  const counts = result.counts;
  const cats = result.categories.map((c) => {
    const color = c.score >= 90 ? "var(--ok)" : c.score >= 70 ? "var(--suggestion)" : c.score >= 40 ? "var(--warning)" : "var(--error)";
    const parts = [c.errors && `${c.errors} bł.`, c.warnings && `${c.warnings} ostrz.`, c.suggestions && `${c.suggestions} sug.`].filter(Boolean);
    return `<div class="cat-row">
      <div>${esc(c.label)}<small>${parts.length ? parts.join(" · ") : "bez uwag"}</small></div>
      <div class="bar"><i style="width:${c.score}%;background:${color}"></i></div>
      <div class="num">${c.score}</div>
    </div>`;
  }).join("");
  $("#score-card").innerHTML = `
    <div class="score-top">
      ${ring(result.score, result.verdict)}
      <div>
        <div class="verdict" style="color:${VERDICT_COLOR[result.verdict]}">${esc(result.verdict_label)}</div>
        <div class="counts">
          <span class="pill pill-error">${plural(counts.error, "błąd", "błędy", "błędów")}</span>
          <span class="pill pill-warning">${plural(counts.warning, "ostrzeżenie", "ostrzeżenia", "ostrzeżeń")}</span>
          <span class="pill pill-suggestion">${plural(counts.suggestion, "sugestia", "sugestie", "sugestii")}</span>
        </div>
        <div class="source-line">${sourceBadge(result.source)}<span>${plural(result.words, "słowo", "słowa", "słów")}</span></div>
      </div>
    </div>
    ${result.ai_note ? `<p class="muted small" style="margin:0">${esc(result.ai_note)}</p>` : ""}
    <div class="cats">${cats}</div>
    <details class="formula"><summary>Jak liczony jest wynik?</summary><p>${esc(result.formula)}</p></details>`;
}

function renderHighlights(text, findings) {
  const chars = Array.from(text);
  const spans = findings.filter((f) => f.start !== null && f.end > f.start);
  const points = new Set([0, chars.length]);
  spans.forEach((f) => { points.add(f.start); points.add(f.end); });
  const sorted = [...points].sort((a, b) => a - b);
  let html = "";
  for (let i = 0; i < sorted.length - 1; i++) {
    const a = sorted[i], b = sorted[i + 1];
    const segment = chars.slice(a, b).join("");
    const active = spans.filter((f) => f.start <= a && f.end >= b)
      .sort((x, y) => SEV_ORDER[x.severity] - SEV_ORDER[y.severity]);
    if (!active.length) { html += esc(segment); continue; }
    const cls = `sev-${active[0].severity}` + (active.some((f) => f.source === "ai") ? " src-ai" : "");
    const title = active.map((f) => `${SEV_LABEL[f.severity]}: ${f.title}`).join("\n");
    html += `<mark class="${cls}" data-ids="${active.map((f) => f.id).join(" ")}" title="${esc(title)}">${esc(segment)}</mark>`;
  }
  $("#highlighted").innerHTML = html;
  const docLevel = findings.filter((f) => f.start === null);
  $("#doc-level").innerHTML = docLevel.length
    ? `<span class="muted small">Dotyczy całego ogłoszenia (brakujące elementy):</span>` +
      docLevel.map((f) => `<button type="button" data-id="${f.id}" class="doc-btn"><span class="pill pill-${f.severity}">${SEV_LABEL[f.severity]}</span> ${esc(f.title)}</button>`).join("")
    : "";
}

function fixLine(f) {
  if (!f.fix || f.fix.mode === "title_marker") return f.fix ? `<p class="fixline">Automatyczna poprawka: dopisanie „${esc(f.fix.text.trim())}” do tytułu.</p>` : "";
  if (f.fix.text.trim() && ["replace", "word_map"].includes(f.fix.mode)) {
    return `<p class="fixline">Automatyczna poprawka: „${esc(f.fragment)}” → „${esc(f.fix.text.trim())}”</p>`;
  }
  return `<p class="fixline">Automatyczna poprawka: usunięcie fragmentu.</p>`;
}

function renderFindings(result) {
  const list = $("#findings");
  const items = result.findings.filter((f) => state.filter === "all" || f.severity === state.filter);
  $("#findings-count").textContent = `(${result.findings.length})`;
  list.innerHTML = items.length ? items.map((f) => `
    <li class="finding sev-${f.severity}" id="finding-${f.id}" data-id="${f.id}">
      <div class="f-head">
        <span class="sev">${SEV_LABEL[f.severity]}</span>
        <span class="tag">${esc(categoryLabel(f.category))}</span>
        ${f.source === "ai" ? '<span class="tag tag-ai">AI</span>' : `<span class="tag">${esc(f.rule_id)}</span>`}
      </div>
      <h3>${esc(f.title)}</h3>
      ${f.fragment ? `<blockquote data-id="${f.id}" title="Pokaż w tekście">„${esc(f.fragment)}”</blockquote>` : '<p class="muted small">Dotyczy całego ogłoszenia.</p>'}
      <p>${esc(f.explanation)}</p>
      <p><strong>Propozycja:</strong> ${esc(f.suggestion)}</p>
      ${fixLine(f)}
      <p class="law"><strong>Podstawa:</strong> ${esc(f.legal_basis)}</p>
    </li>`).join("") : '<li class="muted">Brak znalezisk w tym filtrze.</li>';
  const rejected = result.ai_rejected || [];
  $("#rejected").hidden = !rejected.length;
  $("#rejected-list").innerHTML = rejected.map((r) => `<li>„${esc(r.quote)}” — ${esc(r.reason)}</li>`).join("");
}

function categoryLabel(cat) {
  const found = (state.lastAudit?.categories || []).find((c) => c.category === cat);
  return found ? found.label : cat;
}

function focusFinding(id) {
  const el = document.getElementById(`finding-${id}`);
  if (!el) {
    state.filter = "all";
    $$(".filters .chip").forEach((c) => c.classList.toggle("active", c.dataset.filter === "all"));
    renderFindings(state.lastAudit);
  }
  const target = document.getElementById(`finding-${id}`);
  if (!target) return;
  target.scrollIntoView({ behavior: "smooth", block: "nearest" });
  target.classList.add("flash");
  setTimeout(() => target.classList.remove("flash"), 1200);
}

function focusMark(id) {
  $$("#highlighted mark").forEach((m) => m.classList.remove("active"));
  const marks = $$("#highlighted mark").filter((m) => m.dataset.ids.split(" ").includes(id));
  marks.forEach((m) => m.classList.add("active"));
  if (marks[0]) marks[0].scrollIntoView({ behavior: "smooth", block: "center" });
}

/* ------------------------------------------------------------------ przepisanie */
async function runRewrite() {
  const button = $("#rewrite-btn");
  setBusy(button, true, "Przepisuję…");
  try {
    const result = await api("/api/rewrite", { method: "POST", body: JSON.stringify({ text: state.lastText, use_ai: $("#use-ai").checked }) });
    state.rewrite = result;
    renderRewrite(result);
  } catch (err) {
    showError($("#audit-error"), err.message);
  } finally {
    setBusy(button, false);
  }
}

function renderRewrite(result) {
  $("#rewrite-body").hidden = false;
  $("#rewrite-source").innerHTML = result.source === "ai"
    ? '<span class="badge badge-ai">źródło: AI</span>' : '<span class="badge badge-offline">źródło: offline (reguły)</span>';
  const delta = result.score_after - result.score_before;
  $("#rewrite-meta").innerHTML = `
    <span>Wynik reguł: <span class="score-change">${result.score_before} → ${result.score_after}</span>
      ${delta ? `<span class="pill ${delta > 0 ? "badge-ok" : "pill-error"}">${delta > 0 ? "+" : ""}${delta}</span>` : ""}</span>
    <span class="muted">${plural(result.changes.length, "zmiana", "zmiany", "zmian")}</span>
    ${result.ai_note ? `<span class="muted small">${esc(result.ai_note)}</span>` : ""}`;
  $("#rewrite-warnings").innerHTML = result.warnings.map((w) => `<li>${esc(w)}</li>`).join("");
  $("#view-diff").innerHTML = renderDiff(state.lastText, result.text);
  $("#view-text").innerHTML = esc(result.text).replace(/\[UZUPEŁNIJ:[^\]]*\]/g, (m) => `<span class="ph">${m}</span>`);
  $("#view-changes").innerHTML = result.changes.map((c) => `
    <li class="change">
      <span class="ck ck-${c.kind}">${KIND_LABEL[c.kind]}</span>${esc(c.reason)}
      ${!c.verified ? '<span class="unverified"> (fragment niezweryfikowany w oryginale)</span>' : ""}
      <div>${c.before ? `<span class="before">${esc(c.before)}</span>` : ""}${c.before && c.after ? " → " : ""}${c.after ? `<span class="after">${esc(c.after)}</span>` : ""}</div>
    </li>`).join("") || '<li class="muted">Brak zmian.</li>';
  setView("diff");
}

function setView(view) {
  $$(".seg .chip").forEach((c) => c.classList.toggle("active", c.dataset.view === view));
  $("#view-diff").hidden = view !== "diff";
  $("#view-text").hidden = view !== "text";
  $("#view-changes").hidden = view !== "changes";
}

function tokenize(s) {
  return s.match(/[\p{L}\p{N}_]+|\s+|[^\p{L}\p{N}_\s]/gu) || [];
}

// LCS na dwóch listach tokenów; zwraca scalone operacje eq/del/ins.
function diffSeq(A, B) {
  const n = A.length, m = B.length;
  if (n * m > 6e6) return [{ op: "del", items: A }, { op: "ins", items: B }];
  const w = m + 1;
  const dp = new Uint32Array((n + 1) * w);
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i * w + j] = A[i] === B[j] ? dp[(i + 1) * w + j + 1] + 1 : Math.max(dp[(i + 1) * w + j], dp[i * w + j + 1]);
    }
  }
  const ops = [];
  const push = (op, item) => {
    const last = ops[ops.length - 1];
    if (last && last.op === op) last.items.push(item); else ops.push({ op, items: [item] });
  };
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (A[i] === B[j]) { push("eq", A[i]); i++; j++; }
    else if (dp[(i + 1) * w + j] >= dp[i * w + j + 1]) { push("del", A[i]); i++; }
    else { push("ins", B[j]); j++; }
  }
  while (i < n) push("del", A[i++]);
  while (j < m) push("ins", B[j++]);
  return ops;
}

function renderOps(ops) {
  return ops.map((o) => {
    const text = esc(o.items.join(""));
    return o.op === "eq" ? text : o.op === "del" ? `<del>${text}</del>` : `<ins>${text}</ins>`;
  }).join("");
}

// Podobieństwo linii (Dice na zbiorach słów); puste linie są „równe” tylko identycznym.
function wordSet(s) {
  return new Set(tokenize(s).filter((t) => /[\p{L}\p{N}]/u.test(t)));
}

function similar(a, b, sa, sb) {
  if (a === b) return true;
  if (!sa.size || !sb.size) return false;
  let common = 0;
  sa.forEach((t) => { if (sb.has(t)) common++; });
  return (2 * common) / (sa.size + sb.size) >= 0.5;
}

// LCS po liniach, w którym podobne linie też się parują; sparowane i zmienione linie → różnice słów.
function renderDiff(a, b) {
  const A = a.split(/(?<=\n)/), B = b.split(/(?<=\n)/);
  const n = A.length, m = B.length;
  if (n * m > 4e6) return renderOps(diffSeq(tokenize(a), tokenize(b)));
  const SA = A.map(wordSet), SB = B.map(wordSet);
  const w = m + 1;
  const same = new Uint8Array(n * m);
  for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) same[i * m + j] = similar(A[i], B[j], SA[i], SB[j]) ? 1 : 0;
  const dp = new Uint32Array((n + 1) * w);
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i * w + j] = same[i * m + j] ? dp[(i + 1) * w + j + 1] + 1 : Math.max(dp[(i + 1) * w + j], dp[i * w + j + 1]);
    }
  }
  let html = "", i = 0, j = 0;
  while (i < n && j < m) {
    if (same[i * m + j] && dp[i * w + j] === dp[(i + 1) * w + j + 1] + 1) {
      html += A[i] === B[j] ? esc(A[i]) : renderOps(diffSeq(tokenize(A[i]), tokenize(B[j])));
      i++; j++;
    } else if (dp[(i + 1) * w + j] >= dp[i * w + j + 1]) {
      html += `<del>${esc(A[i++])}</del>`;
    } else {
      html += `<ins>${esc(B[j++])}</ins>`;
    }
  }
  while (i < n) html += `<del>${esc(A[i++])}</del>`;
  while (j < m) html += `<ins>${esc(B[j++])}</ins>`;
  return html;
}

/* ------------------------------------------------------------------ wsad */
async function loadSample(format) {
  try {
    const sample = await api(`/api/batch/sample?format=${format}`);
    $("#batch-content").value = sample.content;
    $("#batch-format").value = format;
    showError($("#batch-error"), "");
  } catch (err) {
    showError($("#batch-error"), err.message);
  }
}

function readFile(file) {
  const reader = new FileReader();
  reader.onload = () => {
    $("#batch-content").value = reader.result;
    const ext = file.name.split(".").pop().toLowerCase();
    $("#batch-format").value = ext === "json" ? "json" : ext === "csv" ? "csv" : "auto";
  };
  reader.onerror = () => showError($("#batch-error"), "Nie udało się odczytać pliku.");
  reader.readAsText(file, "utf-8");
}

async function runBatch() {
  const content = $("#batch-content").value;
  const button = $("#batch-btn");
  showError($("#batch-error"), "");
  if (!content.trim()) { showError($("#batch-error"), "Wczytaj plik, wklej zawartość albo użyj przykładu."); return; }
  setBusy(button, true, "Analizuję…");
  try {
    const report = await api("/api/batch/import", { method: "POST", body: JSON.stringify({ content, format: $("#batch-format").value }) });
    renderBatch(report);
  } catch (err) {
    showError($("#batch-error"), err.message);
  } finally {
    setBusy(button, false);
  }
}

function renderBatch(r) {
  $("#batch-results").hidden = false;
  $("#batch-tiles").innerHTML = [
    [r.count, plural(r.count, "ogłoszenie", "ogłoszenia", "ogłoszeń").replace(/^\d+ /, "")], [r.average_score, "średni wynik"], [r.median_score, "mediana"],
    [`${r.with_errors} / ${r.count}`, "z błędami"], [`${r.min_score}–${r.max_score}`, "zakres wyników"],
  ].map(([v, l]) => `<div class="tile"><strong>${esc(v)}</strong><span>${esc(l)}</span></div>`).join("");
  $("#batch-top").innerHTML = r.top_rules.map((s) => `
    <li><div class="row"><span><span class="pill pill-${s.severity}">${SEV_LABEL[s.severity]}</span> ${esc(s.title)}</span>
      <strong>${Math.round(s.share * 100)}%</strong></div>
      <div class="track"><i style="width:${s.share * 100}%;background:var(--${s.severity})"></i></div>
      <span class="muted small">${s.ads} ogł. · ${plural(s.occurrences, "wystąpienie", "wystąpienia", "wystąpień")}</span></li>`).join("");
  $("#batch-cats").innerHTML = `<tr><th>Kategoria</th><th class="num">Znaleziska</th><th class="num">Ogłoszenia</th></tr>` +
    r.categories.map((c) => `<tr><td>${esc(c.label)}</td><td class="num">${c.findings}</td><td class="num">${c.ads}</td></tr>`).join("");
  $("#batch-verdicts").innerHTML = Object.entries(r.verdicts)
    .map(([k, v]) => `<span class="pill v-${k}">${VERDICT_LABEL[k]}: ${v}</span>`).join("");
  $("#batch-items").innerHTML = `<tr><th class="num">Wynik</th><th>Ocena</th><th>Ogłoszenie</th><th class="num">Błędy</th><th class="num">Ostrz.</th><th class="num">Sug.</th></tr>` +
    r.items.map((i) => `<tr><td class="num"><strong>${i.score}</strong></td><td><span class="pill v-${i.verdict}">${esc(i.verdict_label)}</span></td>
      <td>${esc(i.title || i.id)}</td><td class="num">${i.errors}</td><td class="num">${i.warnings}</td><td class="num">${i.suggestions}</td></tr>`).join("");
  $("#batch-skipped").innerHTML = r.skipped.map((s) => `<li>${esc(s)}</li>`).join("");
}

/* ------------------------------------------------------------------ reguły */
async function loadRules() {
  try {
    const rules = await api("/api/rules");
    $("#rules-count").textContent = plural(rules.length, "reguła", "reguły", "reguł");
    const groups = {};
    rules.forEach((r) => (groups[r.category_label] ||= []).push(r));
    $("#rules-list").innerHTML = Object.entries(groups).map(([label, list]) => `
      <div class="rule-group"><h3>${esc(label)}</h3>${list.map((r) => `
        <div class="rule">
          <div class="meta"><span class="pill pill-${r.severity}">${esc(r.severity_label)}</span><code>${esc(r.id)}</code>
            <span class="tag">${r.kind === "missing" ? "brak elementu" : r.kind === "check" ? "sprawdzenie" : plural(r.patterns, "wzorzec", "wzorce", "wzorców")}</span>
            <span class="tag">${plural(r.exceptions, "wyjątek", "wyjątki", "wyjątków")}</span>${r.auto_fix ? '<span class="tag">poprawka offline</span>' : ""}</div>
          <strong>${esc(r.title)}</strong>
          <p>${esc(r.explanation)}</p>
          <p class="muted small"><strong>Podstawa:</strong> ${esc(r.legal_basis)}</p>
        </div>`).join("")}</div>`).join("");
  } catch (err) {
    $("#rules-list").innerHTML = `<p class="error">${esc(err.message)}</p>`;
  }
}

/* ------------------------------------------------------------------ zdarzenia */
function bind() {
  $$(".tab").forEach((t) => t.addEventListener("click", () => switchTab(t.dataset.tab)));
  $("#ad-text").addEventListener("input", updateCount);
  $("#ad-text").addEventListener("keydown", (e) => { if ((e.ctrlKey || e.metaKey) && e.key === "Enter") runAudit(); });
  $("#audit-btn").addEventListener("click", runAudit);
  $("#clear-btn").addEventListener("click", () => {
    $("#ad-text").value = "";
    updateCount();
    $("#results").hidden = true;
    $("#rewrite-card").hidden = true;
    $("#injection-banner").hidden = true;
  });
  $$(".filters .chip").forEach((c) => c.addEventListener("click", () => {
    state.filter = c.dataset.filter;
    $$(".filters .chip").forEach((x) => x.classList.toggle("active", x === c));
    renderFindings(state.lastAudit);
  }));
  $("#highlighted").addEventListener("click", (e) => {
    const mark = e.target.closest("mark");
    if (!mark) return;
    const ids = mark.dataset.ids.split(" ");
    const key = mark.dataset.ids;
    state.cycle[key] = ((state.cycle[key] ?? -1) + 1) % ids.length;
    focusFinding(ids[state.cycle[key]]);
  });
  $("#doc-level").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-id]");
    if (btn) focusFinding(btn.dataset.id);
  });
  $("#findings").addEventListener("click", (e) => {
    const quote = e.target.closest("blockquote[data-id]");
    if (quote) focusMark(quote.dataset.id);
  });
  $("#rewrite-btn").addEventListener("click", runRewrite);
  $$(".seg .chip").forEach((c) => c.addEventListener("click", () => setView(c.dataset.view)));
  $("#copy-btn").addEventListener("click", async () => {
    if (!state.rewrite) return;
    try { await navigator.clipboard.writeText(state.rewrite.text); $("#copy-btn").textContent = "Skopiowano"; }
    catch { $("#copy-btn").textContent = "Kopiowanie niedostępne"; }
    setTimeout(() => ($("#copy-btn").textContent = "Kopiuj tekst"), 1500);
  });
  $("#use-btn").addEventListener("click", () => {
    if (!state.rewrite) return;
    $("#ad-text").value = state.rewrite.text;
    updateCount();
    window.scrollTo({ top: 0, behavior: "smooth" });
    runAudit();
  });
  $("#sample-csv").addEventListener("click", () => loadSample("csv"));
  $("#sample-json").addEventListener("click", () => loadSample("json"));
  $("#batch-file").addEventListener("change", (e) => { if (e.target.files[0]) readFile(e.target.files[0]); });
  $("#batch-btn").addEventListener("click", runBatch);
}

bind();
loadHealth();
loadExamples();
updateCount();

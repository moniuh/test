#!/usr/bin/env node
// Generuje docs/POMYSLY-100.md z docs/pomysly.json.
// Użycie: node docs/scripts/generuj-pomysly.mjs [--check]
//   --check  kończy się błędem, jeśli wygenerowany plik różni się od zapisanego (do CI).
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const docsDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const data = JSON.parse(readFileSync(join(docsDir, 'pomysly.json'), 'utf8'));
const realizacja = existsSync(join(docsDir, 'realizacja.json'))
  ? JSON.parse(readFileSync(join(docsDir, 'realizacja.json'), 'utf8'))
  : {};

const wagi = data.model.wymiary.map((w) => w.waga);
const wynik = (s) => Math.round(s.reduce((acc, v, i) => acc + v * wagi[i], 0) * 20 * 10) / 10;

const pomysly = data.pomysly.map((p) => ({ ...p, wynik: wynik(p.s) }));
const ranking = [...pomysly].sort(
  (a, b) => b.wynik - a.wynik || b.s[0] - a.s[0] || b.s[1] - a.s[1] || b.s[2] - a.s[2] || a.id - b.id,
);
ranking.forEach((p, i) => {
  p.miejsce = i + 1;
  p.top = i < 50;
});

if (pomysly.length !== 100) throw new Error(`Oczekiwano 100 pomysłów, jest ${pomysly.length}`);
const brak = ranking.filter((p) => p.top && !realizacja[p.id]);
const fmt = (n) => n.toFixed(1).replace('.', ',');
const oceny = (s) => data.model.wymiary.map((w, i) => `${w.kod}${s[i]}`).join(' ');

const linie = [];
linie.push('# Radar Pracy — 100 pomysłów na rozwój i ranking');
linie.push('');
linie.push('> Plik generowany przez `node docs/scripts/generuj-pomysly.mjs` z `docs/pomysly.json` i `docs/realizacja.json`.');
linie.push('> Nie edytuj go ręcznie.');
linie.push('');
linie.push('## Model oceny');
linie.push('');
linie.push(data.model.opis);
linie.push('');
linie.push('| Kod | Wymiar | Waga | 5 oznacza | 1 oznacza |');
linie.push('|---|---|---|---|---|');
for (const w of data.model.wymiary) {
  linie.push(`| ${w.kod} | ${w.nazwa} | ${Math.round(w.waga * 100)}% | ${w.piec} | ${w.jeden} |`);
}
linie.push('');
if (data.cel) {
  linie.push(`**Cel produktu:** ${data.cel}`);
  linie.push('');
}
linie.push('Punktem wyjścia jest MVP: profil z CV, oferty wklejane jako tekst, dopasowanie');
linie.push('wymaganie po wymaganiu z cytatem z CV jako dowodem oraz prosty tracker aplikacji. Każdy z 100');
linie.push('pomysłów rozszerza to MVP. **50 najwyżej ocenionych zostało zaimplementowanych.** Część pomysłów');
linie.push('spoza top 50 też jest gotowa (oznaczone ✅ w backlogu) — powstały przed doprecyzowaniem celu');
linie.push('produktu albo jako naturalne uzupełnienie innych funkcji.');
linie.push('');
linie.push('## Podsumowanie wg kategorii');
linie.push('');
linie.push('| Kategoria | Pomysłów | W top 50 | Średni wynik |');
linie.push('|---|---|---|---|');
for (const [kod, nazwa] of Object.entries(data.kategorie)) {
  const w = pomysly.filter((p) => p.kat === kod);
  const top = ranking.filter((p) => p.kat === kod && p.top).length;
  const sr = w.reduce((a, p) => a + p.wynik, 0) / w.length;
  linie.push(`| ${kod}. ${nazwa} | ${w.length} | ${top} | ${fmt(sr)} |`);
}
linie.push('');
linie.push('## Ranking — top 50 (zaimplementowane)');
linie.push('');
linie.push('| # | Pomysł | Kat. | Oceny | Wynik | Gdzie w kodzie |');
linie.push('|---|---|---|---|---|---|');
for (const p of ranking.filter((x) => x.top)) {
  const gdzie = realizacja[p.id] ? realizacja[p.id] : '_do zrobienia_';
  linie.push(`| ${p.miejsce} | **#${p.id}** ${p.tytul} | ${p.kat} | ${oceny(p.s)} | **${fmt(p.wynik)}** | ${gdzie} |`);
}
linie.push('');
linie.push('## Ranking — miejsca 51–100 (backlog)');
linie.push('');
linie.push('| # | Pomysł | Kat. | Oceny | Wynik | Stan |');
linie.push('|---|---|---|---|---|---|');
for (const p of ranking.filter((x) => !x.top)) {
  const stan = realizacja[p.id] ? `✅ ${realizacja[p.id]}` : '—';
  linie.push(`| ${p.miejsce} | #${p.id} ${p.tytul} | ${p.kat} | ${oceny(p.s)} | ${fmt(p.wynik)} | ${stan} |`);
}
linie.push('');
linie.push('## Karty pomysłów');
for (const [kod, nazwa] of Object.entries(data.kategorie)) {
  linie.push('');
  linie.push(`### ${kod}. ${nazwa}`);
  linie.push('');
  for (const p of pomysly.filter((x) => x.kat === kod)) {
    const znak = realizacja[p.id] ? '✅' : '⬜';
    linie.push(`- ${znak} **#${p.id}. ${p.tytul}** — ${p.opis} _(${oceny(p.s)} → ${fmt(p.wynik)}, miejsce ${p.miejsce})_`);
  }
}
linie.push('');

const tresc = linie.join('\n');
const cel = join(docsDir, 'POMYSLY-100.md');
if (process.argv.includes('--check')) {
  const obecna = existsSync(cel) ? readFileSync(cel, 'utf8') : '';
  if (obecna !== tresc) {
    console.error('docs/POMYSLY-100.md jest nieaktualny — uruchom node docs/scripts/generuj-pomysly.mjs');
    process.exit(1);
  }
  if (brak.length) {
    console.error(`Brak opisu realizacji dla: ${brak.map((p) => '#' + p.id).join(', ')}`);
    process.exit(1);
  }
} else {
  writeFileSync(cel, tresc);
  console.log(`Zapisano ${cel}`);
  console.log('Top 50:', ranking.filter((p) => p.top).map((p) => `#${p.id}(${p.wynik})`).join(' '));
  console.log('Zrealizowane łącznie:', Object.keys(realizacja).length, '— poza top 50:',
    ranking.filter((p) => !p.top && realizacja[p.id]).map((p) => '#' + p.id).join(' '));
  console.log('Miejsca 51-56:', ranking.slice(50, 56).map((p) => `#${p.id}(${p.wynik})`).join(' '));
  if (brak.length) console.log(`Bez realizacji: ${brak.map((p) => '#' + p.id).join(', ')}`);
}

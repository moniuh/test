/** Czysta logika formatowania modułu aplikacji (testowana w format.test.ts). */

/** Polska odmiana liczebników: 1 linia, 2–4 linie, 5+ linii (z wyjątkiem 12–14). */
export function pluralPl(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n);
  if (abs === 1) return one;
  const mod10 = abs % 10;
  const mod100 = abs % 100;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

/** „1 dzień”, „2 dni”, „5 dni”. */
export function daysText(n: number): string {
  return `${Math.abs(n)} ${Math.abs(n) === 1 ? 'dzień' : 'dni'}`;
}

/** Opis terminu follow-upu względem dziś (`days` < 0 = po terminie). */
export function followUpLabel(days: number): string {
  if (days < 0) return `${daysText(days)} po terminie`;
  if (days === 0) return 'dziś';
  if (days === 1) return 'jutro';
  return `za ${daysText(days)}`;
}

/** Liczba dni od dziś do daty ISO (YYYY-MM-DD); dodatnia = w przyszłości. */
export function daysUntil(iso: string, todayIso: string): number {
  const d = Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10));
  const t = Date.UTC(+todayIso.slice(0, 4), +todayIso.slice(5, 7) - 1, +todayIso.slice(8, 10));
  return Math.round((d - t) / 86_400_000);
}

/** „Wysłana 3 dni temu” / „dziś”. */
export function agoLabel(days: number | null): string | null {
  if (days == null) return null;
  if (days <= 0) return 'dziś';
  if (days === 1) return 'wczoraj';
  return `${daysText(days)} temu`;
}

/** Podsumowanie zmian w CV od wysłania (#46). */
export function diffSummary(added: number, removed: number): string {
  const parts: string[] = [];
  if (added > 0) parts.push(`dodano ${added} ${pluralPl(added, 'linię', 'linie', 'linii')}`);
  if (removed > 0) parts.push(`usunięto ${removed} ${pluralPl(removed, 'linię', 'linie', 'linii')}`);
  return parts.length ? parts.join(', ') : 'bez zmian';
}

/** Wartość dla `<input type="datetime-local">` z LocalDateTime backendu („2026-11-03T14:00:00” → „2026-11-03T14:00”). */
export function toLocalInput(value: string | null | undefined): string {
  if (!value) return '';
  return value.slice(0, 16);
}

/** LocalDateTime dla backendu z `<input type="datetime-local">` („2026-11-03T14:00” → „2026-11-03T14:00:00”). */
export function fromLocalInput(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

/** Czy tekst wygląda na adres URL (np. link do spotkania online). */
export function isUrl(value: string | null | undefined): boolean {
  return !!value && /^https?:\/\/\S+$/i.test(value.trim());
}

/** Link `mailto:` z tematem i treścią (adres może być pusty — program pocztowy zapyta o odbiorcę). */
export function mailtoHref(email: string | null | undefined, subject: string, body: string): string {
  const to = email ? encodeURIComponent(email.trim()).replace(/%40/g, '@') : '';
  return `mailto:${to}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

/** Adres e-mail z pola „Imię Nazwisko <e-mail>”. */
export function emailFrom(recipient: string | null | undefined): string | null {
  if (!recipient) return null;
  const m = recipient.match(/<([^>]+)>/);
  if (m) return m[1];
  return /^\S+@\S+$/.test(recipient.trim()) ? recipient.trim() : null;
}

/** Opisy znaczników szablonów wiadomości (#43) — do komunikatu o brakujących danych. */
export const PLACEHOLDER_LABELS: Record<string, string> = {
  firma: 'nazwa firmy',
  stanowisko: 'stanowisko',
  kontakt: 'osoba kontaktowa (sekcja „Kontakt i oczekiwania”)',
  data_wyslania: 'data wysłania aplikacji',
  data_rozmowy: 'termin rozmowy (sekcja „Rozmowy”)',
  imie_nazwisko: 'imię i nazwisko (profil)',
};

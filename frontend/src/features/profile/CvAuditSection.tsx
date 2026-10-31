import type { CvVariant } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Badge, ErrorBox, Loading, ScoreRing, SourceBadge } from '../../components/ui';
import { cvToolsApi } from './api';
import { CHECK_STATUS, auditCountsText, pluralize, potentialGain, sortChecks } from './cvTools';
import type { AuditCheck, CvAudit } from './types';

/** #4 — audyt CV pod ATS: wynik 0–100 i lista kontroli ze statusem (tekst + ikona + kolor). */
export function CvAuditSection({ cv }: { cv: CvVariant }) {
  const audit = useAsync(() => cvToolsApi.audit(cv.id), [cv.id, cv.updatedAt]);
  const a = audit.loading ? undefined : audit.data;
  return (
    <>
      {/* region ogłoszeń istnieje stale, więc czytnik ekranu odczyta wynik po zakończeniu audytu */}
      <p className="sr-only" role="status" aria-live="polite">
        {a ? `Audyt CV „${a.cvName}”: ${a.score} na 100. ${auditCountsText(a.checks)}.` : ''}
      </p>
      {audit.loading && !audit.data && <Loading label="Sprawdzam CV…" />}
      {audit.error && <ErrorBox message={audit.error} onRetry={audit.reload} />}
      {audit.data && <AuditBody audit={audit.data} />}
    </>
  );
}

function AuditBody({ audit: a }: { audit: CvAudit }) {
  const sorted = sortChecks(a.checks);
  const problems = sorted.filter((c) => c.status !== 'ok');
  const passed = sorted.filter((c) => c.status === 'ok');
  const gain = potentialGain(a.checks);

  return (
    <div className="stack">
      <div className="profile-score">
        <ScoreRing score={a.score} size={84} label="Wynik audytu ATS" />
        <div>
          <p>
            <strong>{a.grade}</strong> <SourceBadge source="offline" />
          </p>
          <p className="muted small">
            {auditCountsText(a.checks)} · {a.stats.words} {pluralize(a.stats.words, 'słowo', 'słowa', 'słów')}
            {gain > 0 ? ` · do zdobycia jeszcze ${gain} pkt` : ''}
          </p>
        </div>
      </div>

      {problems.length > 0 ? (
        <section aria-label="Do poprawy">
          <h4>Do poprawy ({problems.length})</h4>
          <ul className="list-plain">
            {problems.map((c) => (
              <CheckItem key={c.code} check={c} />
            ))}
          </ul>
        </section>
      ) : (
        <Alert tone="success">✓ Wszystkie kontrole spełnione.</Alert>
      )}

      {passed.length > 0 && (
        <details>
          <summary className="label">Spełnione kontrole ({passed.length})</summary>
          <ul className="list-plain" style={{ marginTop: '0.5rem' }}>
            {passed.map((c) => (
              <CheckItem key={c.code} check={c} />
            ))}
          </ul>
        </details>
      )}

      <p className="muted small" style={{ margin: 0 }}>
        Wynik liczą reguły, bez AI: każda kontrola ma jawną wagę (suma 100), „do poprawy” daje połowę punktów, „błąd” —
        zero. Popraw treść CV w edytorze powyżej i zapisz, a audyt przeliczy się automatycznie.
      </p>
    </div>
  );
}

function CheckItem({ check }: { check: AuditCheck }) {
  const meta = CHECK_STATUS[check.status];
  return (
    <li className={`profile-check profile-check-${check.status}`}>
      <div className="profile-check-head">
        <span>
          <span className="profile-icon" aria-hidden="true">
            {meta.icon}
          </span>
          <strong>{check.title}</strong>
        </span>
        <span className="row" style={{ gap: '0.5rem' }}>
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="profile-points">
            {formatPoints(check.points)}/{check.weight} pkt
          </span>
        </span>
      </div>
      <p className="small">{check.details}</p>
      {check.fix && (
        <p className="small">
          <strong>Jak poprawić:</strong> {check.fix}
        </p>
      )}
      {check.examples.length > 0 && (
        <div className="small">
          <span className="muted">Przykłady z CV:</span>
          {check.examples.map((e) => (
            <blockquote key={e} className="evidence related">
              {e}
            </blockquote>
          ))}
        </div>
      )}
    </li>
  );
}

function formatPoints(p: number): string {
  return Number.isInteger(p) ? String(p) : p.toFixed(1).replace('.', ',');
}

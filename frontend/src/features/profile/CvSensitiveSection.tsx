import type { AsyncState } from '../../hooks/useAsync';
import { Alert, Badge, ErrorBox, Loading, SourceBadge } from '../../components/ui';
import { SEVERITY } from './cvTools';
import type { SensitiveReport } from './types';

/**
 * #8 — zbędne dane wrażliwe w CV. Dane ładuje CvToolsPanel (licznik na zakładce), tu tylko prezentacja.
 */
export function CvSensitiveSection({ report }: { report: AsyncState<SensitiveReport | null> }) {
  if (report.loading && !report.data) return <Loading label="Sprawdzam dane osobowe w CV…" />;
  if (report.error) return <ErrorBox message={report.error} onRetry={report.reload} />;
  const r = report.data;
  if (!r) return null;

  if (r.findings.length === 0) {
    return (
      <div className="stack">
        <Alert tone="success">
          <span>✓ {r.summary}</span>
        </Alert>
        <Scope />
      </div>
    );
  }

  return (
    <div className="stack">
      <Alert tone="warning">
        <span>
          {r.summary} <SourceBadge source="offline" />
        </span>
      </Alert>
      <ul className="list-plain">
        {r.findings.map((f, i) => {
          const sev = SEVERITY[f.severity];
          return (
            <li key={`${f.category}-${i}`} className="stack" style={{ gap: '0.3rem' }}>
              <div className="profile-check-head">
                <strong>{f.label}</strong>
                <Badge tone={sev.tone}>{sev.label}</Badge>
              </div>
              <blockquote className={`evidence ${f.severity === 'info' ? 'profile-quote-neutral' : 'profile-quote-danger'}`}>
                <span className="sr-only">Fragment CV: </span>
                {f.quote}
              </blockquote>
              <p className="small" style={{ margin: 0 }}>
                <strong>Dlaczego:</strong> {f.why}
              </p>
              <p className="small" style={{ margin: 0 }}>
                <strong>Co zrobić:</strong> {f.suggestion}
              </p>
            </li>
          );
        })}
      </ul>
      <p className="muted small" style={{ margin: 0 }}>
        Usuń wskazane fragmenty w edytorze powyżej i zapisz CV — lista odświeży się automatycznie.
      </p>
      <Scope />
    </div>
  );
}

function Scope() {
  return (
    <p className="muted small" style={{ margin: 0 }}>
      Sprawdzamy: PESEL (z sumą kontrolną), numer dowodu lub paszportu, datę urodzenia i wiek, pełny adres, stan cywilny,
      dzieci, zdjęcie, wyznanie, zdrowie i niepełnosprawność, dane rodziców oraz narodowość. Zakres danych, których
      pracodawca może żądać od kandydata, określa art. 22¹ Kodeksu pracy. Analiza działa lokalnie, bez AI.
    </p>
  );
}

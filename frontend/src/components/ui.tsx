import { useId, type ReactNode } from 'react';

/** Wspólne, dostępne komponenty interfejsu. */

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </div>
  );
}

export function Card({
  title, children, actions, className = '', headingLevel = 2,
}: {
  title?: ReactNode;
  children: ReactNode;
  actions?: ReactNode;
  className?: string;
  headingLevel?: 2 | 3;
}) {
  const H = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-header">
          {title && <H>{title}</H>}
          {actions && <div className="actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export function Badge({ children, tone = 'neutral', title }: { children: ReactNode; tone?: Tone; title?: string }) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

export function scoreTone(score: number): Tone {
  if (score >= 75) return 'success';
  if (score >= 50) return 'warning';
  return 'danger';
}

/** Wynik dopasowania 0–100 jako pierścień z liczbą (z tekstem dla czytników ekranu). */
export function ScoreRing({ score, size = 72, label = 'Dopasowanie' }: { score: number; size?: number; label?: string }) {
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  const tone = scoreTone(score);
  return (
    <div className={`score-ring tone-${tone}`} style={{ width: size, height: size }} role="img" aria-label={`${label}: ${score} na 100`}>
      <svg width={size} height={size} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} className="ring-bg" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          className="ring-fg"
          strokeDasharray={`${(c * score) / 100} ${c}`}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="score-value" aria-hidden="true">{score}</span>
    </div>
  );
}

export function ScoreBadge({ score }: { score: number | undefined }) {
  if (score == null) return <Badge>—</Badge>;
  return (
    <Badge tone={scoreTone(score)} title="Dopasowanie do aktywnego CV">
      {score}%
    </Badge>
  );
}

export function Loading({ label = 'Ładowanie…' }: { label?: string }) {
  return (
    <p className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" /> {label}
    </p>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="alert alert-danger" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-small" onClick={onRetry}>
          Spróbuj ponownie
        </button>
      )}
    </div>
  );
}

export function Alert({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div className={`alert alert-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty-title">{title}</p>
      {children}
    </div>
  );
}

/** Pole formularza z etykietą powiązaną z kontrolką. */
export function Field({
  label, hint, children, error,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: (id: string, describedBy: string | undefined) => ReactNode;
}) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [hintId, errId].filter(Boolean).join(' ') || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children(id, describedBy)}
      {hint && (
        <small id={hintId} className="muted">
          {hint}
        </small>
      )}
      {error && (
        <small id={errId} className="field-error">
          {error}
        </small>
      )}
    </div>
  );
}

/** Tekst, który ma być ogłoszony czytnikom ekranu (np. „Zapisano”). */
export function LiveMessage({ message }: { message: string | undefined }) {
  return (
    <p className="live-message" role="status" aria-live="polite">
      {message ?? ''}
    </p>
  );
}

/** Źródło wyniku: AI czy reguły offline (zasada przejrzystości). */
export function SourceBadge({ source }: { source: 'ai' | 'offline' | string }) {
  return source === 'ai' ? (
    <Badge tone="info" title="Wygenerowane przez model AI na podstawie zamaskowanych danych">AI</Badge>
  ) : (
    <Badge title="Wynik reguł działających bez AI">offline</Badge>
  );
}

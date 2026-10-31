import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { CvVariant, JobOffer } from '../../api/types';
import { APPLICATION_STATUS, formatDate } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { applicationsApi } from './api';
import { isClosed } from './board';
import { CoverLetterEditor } from './CoverLetterEditor';
import { ReapplyWarning } from './ReapplyWarning';
import { useAction } from './useAction';
import './applications.css';

/**
 * Aplikowanie z poziomu oferty: ostrzeżenie o ponownej aplikacji (#47), wybór wersji CV, status,
 * list motywacyjny (#42) i zapis aplikacji. Gdy aplikacja już istnieje — jej status i link.
 */
export function ApplyPanel({ offer, cvId }: { offer: JobOffer; cvId: number | null }) {
  const apps = useAsync(() => applicationsApi.forOffer(offer.id), [offer.id]);
  const check = useAsync(() => applicationsApi.check(offer.id), [offer.id]);
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const [chosenCv, setChosenCv] = useState<number | null>(null);
  const [status, setStatus] = useState<'APPLIED' | 'PLANNED'>('APPLIED');
  const [letter, setLetter] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [message, setMessage] = useState<string>();
  const { busy, error, run } = useAction();
  const radioName = useId();

  const effectiveCv = chosenCv ?? cvId ?? cvs.data?.[0]?.id ?? null;
  const existing = apps.data ?? [];
  const formVisible = apps.data != null && (existing.length === 0 || showForm);

  async function save(e: FormEvent) {
    e.preventDefault();
    setMessage(undefined);
    const created = await run(() =>
      applicationsApi.create({
        offerId: offer.id,
        cvVariantId: effectiveCv,
        status,
        coverLetter: letter.trim() ? letter.trim() : null,
      }),
    );
    if (!created) return;
    setMessage(
      created.status === 'APPLIED'
        ? `Zapisano wysłaną aplikację.${created.nextFollowUpAt ? ` Przypomnienie o follow-upie: ${formatDate(created.nextFollowUpAt)}.` : ''}`
        : 'Zapisano aplikację do wysłania.',
    );
    setShowForm(false);
    setLetter('');
    apps.reload();
    check.reload();
  }

  return (
    <Card title="Aplikacja" headingLevel={2}>
      {apps.loading && !apps.data && <Loading />}
      {apps.error && <ErrorBox message={apps.error} onRetry={apps.reload} />}
      {existing.length > 0 && (
        <ul className="list-plain">
          {existing.map((a) => (
            <li key={a.id}>
              <Badge tone={a.status === 'REJECTED' ? 'danger' : isClosed(a.status) ? 'neutral' : 'info'}>
                {APPLICATION_STATUS[a.status]}
              </Badge>{' '}
              <Link to={`/aplikacje/${a.id}`}>
                Szczegóły aplikacji<span className="sr-only">: {APPLICATION_STATUS[a.status]}</span>
              </Link>
              <div className="muted small">
                {a.appliedAt ? `wysłana ${formatDate(a.appliedAt)}` : `utworzona ${formatDate(a.createdAt)}`}
                {a.cvVariantName && ` · CV: ${a.cvVariantName}`}
              </div>
            </li>
          ))}
        </ul>
      )}
      {existing.length > 0 && !showForm && (
        <button type="button" className="btn btn-small" onClick={() => setShowForm(true)}>
          Aplikuj ponownie
        </button>
      )}
      {formVisible && (
        <form onSubmit={save} className="stack" style={{ gap: '0.5rem' }}>
          {check.data && <ReapplyWarning check={check.data} />}
          <fieldset>
            <legend>Status</legend>
            <div className="checks">
              <label>
                <input
                  type="radio"
                  name={radioName}
                  value="APPLIED"
                  checked={status === 'APPLIED'}
                  onChange={() => setStatus('APPLIED')}
                />{' '}
                Wysłana
              </label>
              <label>
                <input
                  type="radio"
                  name={radioName}
                  value="PLANNED"
                  checked={status === 'PLANNED'}
                  onChange={() => setStatus('PLANNED')}
                />{' '}
                Do wysłania
              </label>
            </div>
          </fieldset>
          {cvs.data && cvs.data.length > 0 ? (
            <Field label="Wersja CV" hint="Przy wysłanej aplikacji zapiszemy dokładną treść tej wersji (snapshot).">
              {(id, describedBy) => (
                <select
                  id={id}
                  aria-describedby={describedBy}
                  value={effectiveCv ?? ''}
                  onChange={(e) => setChosenCv(Number(e.target.value))}
                >
                  {cvs.data!.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              )}
            </Field>
          ) : (
            cvs.data && (
              <p className="small muted">
                Brak CV w profilu — <Link to="/profil">dodaj CV</Link>, aby list zawierał cytaty-dowody.
              </p>
            )
          )}
          <h3>
            List motywacyjny <span className="small muted">(opcjonalnie)</span>
          </h3>
          <CoverLetterEditor offerId={offer.id} cvId={effectiveCv} value={letter} onChange={setLetter} />
          {error && <ErrorBox message={error} />}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {status === 'APPLIED' ? 'Zapisz wysłaną aplikację' : 'Zapisz do wysłania'}
            </button>
            {showForm && (
              <button type="button" className="btn" onClick={() => setShowForm(false)}>
                Anuluj
              </button>
            )}
          </div>
        </form>
      )}
      <LiveMessage message={message} />
    </Card>
  );
}

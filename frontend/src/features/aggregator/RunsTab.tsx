import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { aggregatorApi } from './api';
import { RUN_STATUS_LABEL, TRIGGER_LABEL, runTone } from './logic';
import type { ArchiveReport, SourceView } from './types';

/** Historia przebiegów pobierania (pomysł #15) i archiwizacja wygasłych ofert (pomysł #22). */
export function RunsTab({
  sources, version, announce, onChanged,
}: {
  sources: SourceView[];
  version: number;
  announce: (message: string) => void;
  onChanged: () => void;
}) {
  const [sourceId, setSourceId] = useState('');
  const runs = useAsync(() => aggregatorApi.runs(sourceId ? Number(sourceId) : null, 100), [sourceId, version]);
  const [report, setReport] = useState<ArchiveReport>();
  const [archiving, setArchiving] = useState(false);
  const [archiveError, setArchiveError] = useState<string>();

  async function archive() {
    setArchiving(true);
    setArchiveError(undefined);
    try {
      const r = await aggregatorApi.archiveExpired();
      setReport(r);
      announce(r.message);
      onChanged();
    } catch (e) {
      setArchiveError(errorMessage(e));
    } finally {
      setArchiving(false);
    }
  }

  return (
    <>
      <h2>Historia pobrań</h2>
      <Card title="Wygasłe oferty" headingLevel={3}>
        <p className="small muted">
          Archiwizuje oferty ze źródeł w stanie „Nowa” lub „Zapisana”, którym minęła data ważności, oraz te bez daty
          pobrane dawno temu. Oferty, do których masz aplikację, i dodane ręcznie zostają bez zmian. Harmonogram robi to
          automatycznie.
        </p>
        <button type="button" className="btn" onClick={archive} disabled={archiving}>
          {archiving ? 'Archiwizowanie…' : 'Archiwizuj wygasłe oferty'}
        </button>
        {archiveError && <ErrorBox message={archiveError} />}
        {report && (
          <div style={{ marginTop: '0.75rem' }}>
            <p><strong>{report.message}</strong></p>
            {report.offers.length > 0 && (
              <ul className="small">
                {report.offers.map((o) => (
                  <li key={o.offerId}>
                    <Link to={`/oferty/${o.offerId}`}>{o.title}</Link>{o.company ? ` (${o.company})` : ''} — {o.reason}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Card>

      <div className="aggregator-toolbar" style={{ marginTop: '1rem' }}>
        <Field label="Źródło">
          {(id) => (
            <select id={id} value={sourceId} onChange={(e) => setSourceId(e.target.value)}>
              <option value="">Wszystkie</option>
              {sources.map((s) => <option key={s.id} value={String(s.id)}>{s.name}</option>)}
            </select>
          )}
        </Field>
      </div>
      {runs.error && <ErrorBox message={runs.error} onRetry={runs.reload} />}
      {runs.loading && !runs.data && <Loading label="Ładowanie historii…" />}
      {runs.data && runs.data.length === 0 && <EmptyState title="Brak przebiegów">Pobierz źródło, aby zobaczyć historię.</EmptyState>}
      {runs.data && runs.data.length > 0 && (
        <div className="table-wrap">
          <table className="aggregator-runs">
            <caption className="sr-only">Historia przebiegów pobierania (od najnowszych)</caption>
            <thead>
              <tr>
                <th scope="col">Kiedy</th>
                <th scope="col">Źródło</th>
                <th scope="col">Wynik</th>
                <th scope="col" className="aggregator-num">Pobrane</th>
                <th scope="col" className="aggregator-num">Nowe</th>
                <th scope="col" className="aggregator-num">Duplikaty</th>
                <th scope="col" className="aggregator-num">Odfiltrowane</th>
                <th scope="col" className="aggregator-num">Błędy</th>
                <th scope="col">Szczegóły</th>
              </tr>
            </thead>
            <tbody>
              {runs.data.map((r) => (
                <tr key={r.id}>
                  <th scope="row">
                    {formatDateTime(r.startedAt)}
                    <br />
                    <span className="muted small">{TRIGGER_LABEL[r.trigger]}</span>
                  </th>
                  <td>{r.sourceName ?? '—'}</td>
                  <td><Badge tone={runTone(r.status)}>{RUN_STATUS_LABEL[r.status]}</Badge></td>
                  <td className="aggregator-num">{r.fetched}</td>
                  <td className="aggregator-num">{r.created}</td>
                  <td className="aggregator-num">{r.duplicates}</td>
                  <td className="aggregator-num">{r.filteredOut}</td>
                  <td className="aggregator-num">{r.errors}</td>
                  <td className="aggregator-message">{r.message ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

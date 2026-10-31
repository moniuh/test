import type { ApplicationView } from '../../api/types';
import { formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Card, ErrorBox, Loading } from '../../components/ui';
import { applicationsApi } from './api';
import { diffSummary } from './format';
import { useAction, type OnChanged } from './useAction';

/** „Co wysłano” (#46): CV i list z chwili wysłania oraz informacja, czy bieżąca wersja CV się różni. */
export function SnapshotCard({ app, version, onChanged }: { app: ApplicationView; version: number; onChanged: OnChanged }) {
  const snap = useAsync(() => applicationsApi.snapshot(app.id), [app.id, version, app.status]);
  const { busy, error, run } = useAction();
  const s = snap.data;

  async function take() {
    const r = await run(() => applicationsApi.takeSnapshot(app.id));
    if (r) onChanged(undefined, 'Zapisano bieżące CV i list jako wysłane dokumenty.');
  }

  return (
    <Card title="Co wysłano">
      {snap.loading && !s && <Loading />}
      {snap.error && <ErrorBox message={snap.error} onRetry={snap.reload} />}
      {error && <ErrorBox message={error} />}
      {s && !s.hasSnapshot && (
        <>
          <p className="muted small">
            {app.status === 'PLANNED'
              ? 'Aplikacja nie została jeszcze wysłana. Po zmianie statusu na „Wysłana” zapiszemy dokładną treść CV i listu.'
              : 'Brak zapisu dokumentów z chwili wysłania.'}
          </p>
          {app.status !== 'PLANNED' && (
            <button type="button" className="btn btn-small" onClick={take} disabled={busy}>
              Zapisz bieżące CV i list jako wysłane
            </button>
          )}
        </>
      )}
      {s && s.hasSnapshot && (
        <div className="stack" style={{ gap: '0.5rem' }}>
          <p className="small" style={{ margin: 0 }}>
            {s.snapshotAt ? `Zapisano ${formatDateTime(s.snapshotAt)}` : 'Zapisano przy wysłaniu'}
            {s.cvVariantName && ` · wersja CV „${s.cvVariantName}”`}
          </p>
          {s.cvChanged === false && (
            <Alert tone="success">
              <span>
                <span aria-hidden="true">✓ </span>Bieżąca wersja CV „{s.currentCvName}” jest taka sama jak wysłana.
              </span>
            </Alert>
          )}
          {s.cvChanged === true && (
            <Alert tone="warning">
              <span>
                <span aria-hidden="true">⚠ </span>CV „{s.currentCvName}” zmieniło się od wysłania (
                {diffSummary(s.linesAdded, s.linesRemoved)}). Przed rozmową przeczytaj wersję, którą zna firma.
              </span>
            </Alert>
          )}
          {s.cvChanged == null && s.cvSnapshot && (
            <Alert tone="info">Nie można porównać — tej wersji CV nie ma już w profilu.</Alert>
          )}
          {s.cvSnapshot ? (
            <details>
              <summary className="label">Wysłane CV</summary>
              <pre className="applications-doc">{s.cvSnapshot}</pre>
            </details>
          ) : (
            <p className="small muted">Zapis nie zawiera CV (w chwili wysłania nie było wersji CV w profilu).</p>
          )}
          {s.coverLetterSnapshot ? (
            <details>
              <summary className="label">Wysłany list motywacyjny</summary>
              <pre className="applications-doc">{s.coverLetterSnapshot}</pre>
            </details>
          ) : (
            <p className="small muted">Do aplikacji nie dołączono listu motywacyjnego.</p>
          )}
          {s.coverLetterChanged && (
            <p className="small muted">List w aplikacji edytowano po wysłaniu — powyżej wersja z chwili wysłania.</p>
          )}
        </div>
      )}
    </Card>
  );
}

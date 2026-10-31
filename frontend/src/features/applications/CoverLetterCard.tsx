import { useEffect, useState } from 'react';
import type { ApplicationView } from '../../api/types';
import { Card, ErrorBox } from '../../components/ui';
import { applicationsApi } from './api';
import { CoverLetterEditor } from './CoverLetterEditor';
import { useAction, type OnChanged } from './useAction';

/** List motywacyjny aplikacji (#42): generowanie, edycja i zapis w aplikacji. */
export function CoverLetterCard({ app, onChanged }: { app: ApplicationView; onChanged: OnChanged }) {
  const [text, setText] = useState(app.coverLetter ?? '');
  const { busy, error, run } = useAction();
  const dirty = text !== (app.coverLetter ?? '');

  useEffect(() => setText(app.coverLetter ?? ''), [app.coverLetter]);

  async function save() {
    const a = await run(() => applicationsApi.saveCoverLetter(app.id, text));
    if (a) onChanged(a, text.trim() ? 'Zapisano list motywacyjny w aplikacji.' : 'Usunięto list motywacyjny z aplikacji.');
  }

  return (
    <Card title="List motywacyjny">
      {app.status !== 'PLANNED' && (
        <p className="small muted">
          Zmiany listu nie zmieniają wersji wysłanej do firmy — tę znajdziesz w sekcji „Co wysłano”.
        </p>
      )}
      <CoverLetterEditor
        offerId={app.offerId}
        cvId={app.cvVariantId}
        value={text}
        onChange={setText}
        actions={
          <button type="button" className="btn btn-small btn-primary" onClick={save} disabled={busy || !dirty}>
            Zapisz list w aplikacji
          </button>
        }
      />
      {dirty && <p className="small muted" style={{ margin: 0 }}>Masz niezapisane zmiany listu.</p>}
      {error && <ErrorBox message={error} />}
    </Card>
  );
}

import { useState } from 'react';
import type { ApplicationView } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Card, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { applicationsApi } from './api';
import { CopyButton } from './CopyButton';
import { PLACEHOLDER_LABELS, emailFrom, mailtoHref } from './format';
import { useAction, type OnChanged } from './useAction';
import type { RenderedMessage } from './types';

/** Szablony wiadomości (#43): wypełnienie danymi aplikacji, edycja, kopiowanie, zapis jako zdarzenie MESSAGE. */
export function MessagesCard({ app, onChanged }: { app: ApplicationView; onChanged: OnChanged }) {
  const templates = useAsync(() => applicationsApi.templates(), []);
  const [templateId, setTemplateId] = useState('');
  const [msg, setMsg] = useState<RenderedMessage>();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [message, setMessage] = useState<string>();
  const { busy, error, run } = useAction();

  const list = templates.data ?? [];
  const suggested = list.filter((t) => t.suggestedFor.includes(app.status));
  const selectedId = templateId || suggested[0]?.id || list[0]?.id || '';
  const selected = list.find((t) => t.id === selectedId);

  async function fill() {
    if (!selectedId) return;
    setMessage(undefined);
    const r = await run(() => applicationsApi.renderMessage(app.id, selectedId));
    if (!r) return;
    setMsg(r);
    setSubject(r.subject);
    setBody(r.body);
    setMessage(`Wypełniono szablon „${r.templateName}”.`);
  }

  async function saveSent() {
    if (!msg) return;
    const r = await run(() => applicationsApi.saveMessage(app.id, msg.templateId, subject, body));
    if (!r) return;
    setMessage('Zapisano wiadomość w historii aplikacji.');
    onChanged(undefined);
  }

  return (
    <Card title="Szablony wiadomości">
      {templates.loading && !templates.data && <Loading />}
      {templates.error && <ErrorBox message={templates.error} onRetry={templates.reload} />}
      {list.length > 0 && (
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <div style={{ flex: '1 1 260px' }}>
            <Field label="Szablon" hint={selected?.description}>
              {(id, describedBy) => (
                <select id={id} aria-describedby={describedBy} value={selectedId} onChange={(e) => setTemplateId(e.target.value)}>
                  {list.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                      {t.suggestedFor.includes(app.status) ? ' (pasuje do etapu)' : ''}
                    </option>
                  ))}
                </select>
              )}
            </Field>
          </div>
          <div className="field">
            <button type="button" className="btn" onClick={fill} disabled={busy || !selectedId}>
              Wypełnij szablon
            </button>
          </div>
        </div>
      )}
      {error && <ErrorBox message={error} />}
      {msg && (
        <div className="stack" style={{ gap: '0.5rem' }}>
          {msg.missing.length > 0 && (
            <Alert tone="warning">
              <span>
                Brakuje danych: {msg.missing.map((m) => PLACEHOLDER_LABELS[m] ?? m).join(', ')}. W tekście zostały
                opisy w nawiasach kwadratowych — uzupełnij je przed wysłaniem.
              </span>
            </Alert>
          )}
          <p className="applications-message-to">
            <strong>Do:</strong> {msg.to ?? <span className="muted">brak osoby kontaktowej w aplikacji</span>}
          </p>
          <Field label="Temat">
            {(id) => <input id={id} type="text" value={subject} onChange={(e) => setSubject(e.target.value)} />}
          </Field>
          <Field label="Treść wiadomości">
            {(id) => <textarea id={id} rows={11} value={body} onChange={(e) => setBody(e.target.value)} />}
          </Field>
          <div className="actions">
            <CopyButton text={`Temat: ${subject}\n\n${body}`} label="Kopiuj temat i treść" onDone={setMessage} />
            <CopyButton text={body} label="Kopiuj treść" onDone={setMessage} />
            <a className="btn btn-small" href={mailtoHref(emailFrom(msg.to), subject, body)}>
              Otwórz w programie pocztowym
            </a>
            <button type="button" className="btn btn-small btn-primary" onClick={saveSent} disabled={busy || !body.trim()}>
              Zapisz jako wysłaną wiadomość
            </button>
          </div>
        </div>
      )}
      <LiveMessage message={message} />
    </Card>
  );
}

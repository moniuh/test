import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ApiError, api, errorMessage } from '../../api/client';
import type { AiStatus, CandidateProfile, CvVariant, JobOffer } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import {
  Alert, Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, Loading, PageHeader, SourceBadge,
} from '../../components/ui';
import { docxUrl, tailoringApi } from './api';
import { isEdited, parseMaxBullets, resultAnnouncement } from './logic';
import type { CheckResult, TailoredDetail, TailoringOptions, TailorPreview } from './types';
import { DiffView, GuardReportView, RoleChangesView, ScoreComparisonView, Tabs } from './TailoringViews';
import './tailoring.css';

type Tab = 'cv' | 'changes' | 'control';

const isTailoredCv = (c: CvVariant) => (c.origin ?? '').startsWith('dopasowane');

/** Generator dopasowanego CV pod ofertę `:offerId` (pomysły #67, #68, #70, #78). */
export function TailorPage() {
  const { offerId: param } = useParams();
  const offerId = Number(param);
  const [search] = useSearchParams();
  const offer = useAsync(() => api.get<JobOffer>(`/api/offers/${offerId}`), [offerId]);
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const profile = useAsync(() => api.get<CandidateProfile>('/api/profile'), []);
  const ai = useAsync(() => api.get<AiStatus>('/api/ai/status'), []);
  const existing = useAsync(() => tailoringApi.list(offerId), [offerId]);
  useDocumentTitle(offer.data ? `Dopasuj CV: ${offer.data.title}` : 'Dopasuj CV');

  const [baseCvId, setBaseCvId] = useState<number | null>(null);
  const [hide, setHide] = useState(false);
  const [maxBullets, setMaxBullets] = useState('');
  const [useAi, setUseAi] = useState(false);
  const [preview, setPreview] = useState<TailorPreview>();
  const [content, setContent] = useState('');
  const [check, setCheck] = useState<{ content: string; result: CheckResult }>();
  const [tab, setTab] = useState<Tab>('cv');
  const [busy, setBusy] = useState<'preview' | 'check' | 'save' | null>(null);
  const [error, setError] = useState<string>();
  const [announce, setAnnounce] = useState<string>();
  const [name, setName] = useState('');
  const [activate, setActivate] = useState(false);
  const [accept, setAccept] = useState(false);
  const [saved, setSaved] = useState<TailoredDetail>();

  // domyślne CV bazowe: ?cvId=… z karty oferty, potem aktywne, potem pierwsze niedopasowane
  useEffect(() => {
    if (baseCvId != null || !cvs.data || !profile.data) return;
    const fromQuery = Number(search.get('cvId'));
    const ids = cvs.data.map((c) => c.id);
    const pick = ids.includes(fromQuery)
      ? fromQuery
      : profile.data.activeCvId != null && ids.includes(profile.data.activeCvId)
        ? profile.data.activeCvId
        : (cvs.data.find((c) => !isTailoredCv(c)) ?? cvs.data[0])?.id ?? null;
    setBaseCvId(pick);
  }, [cvs.data, profile.data, search, baseCvId]);

  if (!Number.isFinite(offerId)) return <ErrorBox message="Niepoprawny adres oferty." />;
  if (offer.loading && !offer.data) return <Loading />;
  if (offer.error) return <ErrorBox message={offer.error} onRetry={offer.reload} />;
  const o = offer.data!;
  const aiAvailable = ai.data?.available ?? false;
  const edited = preview ? isEdited(preview.content, content) : false;
  const currentCheck = check && check.content === content ? check.result : undefined;
  const report = edited ? currentCheck?.guard : preview?.guard;
  const comparison = edited ? currentCheck?.comparison : preview?.comparison;
  const diff = edited ? currentCheck?.diff : preview?.diff;
  const hasViolations = (report?.violations.length ?? 0) > 0;

  function options(): TailoringOptions {
    return { hideIrrelevantBullets: hide, maxBulletsPerRole: parseMaxBullets(maxBullets), useAi: useAi && aiAvailable };
  }

  async function generate(e: FormEvent) {
    e.preventDefault();
    setBusy('preview');
    setError(undefined);
    setSaved(undefined);
    setAnnounce('Generuję podgląd…');
    try {
      const p = await tailoringApi.preview(offerId, baseCvId, options());
      setPreview(p);
      setContent(p.content);
      setCheck(undefined);
      setName(p.suggestedName);
      setAccept(false);
      setTab('cv');
      setAnnounce(resultAnnouncement(p.comparison.before, p.comparison.after, p.guard));
    } catch (err) {
      setError(errorMessage(err));
      setAnnounce(undefined);
    } finally {
      setBusy(null);
    }
  }

  async function runCheck(goToControl = true) {
    setBusy('check');
    setError(undefined);
    try {
      const result = await tailoringApi.check(offerId, baseCvId, content);
      setCheck({ content, result });
      if (goToControl) setTab('control');
      setAnnounce(resultAnnouncement(result.comparison.before, result.comparison.after, result.guard, 'Sprawdzono zmiany'));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!preview) return;
    setBusy('save');
    setError(undefined);
    try {
      const result = await tailoringApi.save({
        offerId,
        baseCvId: preview.baseCvId,
        options: preview.options,
        content,
        name: name.trim() || preview.suggestedName,
        activate,
        acceptViolations: accept,
        source: preview.source,
        edited,
      });
      setSaved(result);
      existing.reload();
      cvs.reload();
      setAnnounce(`Zapisano nową wersję CV „${result.item.name}”.`);
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError && err.status === 422) {
        // strażnik odrzucił zapis — pokaż aktualny raport
        setBusy(null);
        await runCheck(true);
      }
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <p className="small no-print">
        <Link to={`/oferty/${offerId}`}>← Wróć do oferty</Link> · <Link to="/dopasuj-cv">Biblioteka dopasowanych CV</Link>
      </p>
      <PageHeader
        title="Dopasuj CV do oferty"
        subtitle={
          <>
            <strong>{o.title}</strong>
            {o.company ? ` · ${o.company}` : ''}. Powstaje nowa wersja CV: kluczowe kompetencje z wymagań, najtrafniejsze
            punkty na górze — bez dopisywania czegokolwiek, czego nie ma w CV ani w profilu.
          </>
        }
      />
      <LiveMessage message={announce} />

      {existing.data && existing.data.length > 0 && (
        <Alert tone="info">
          <span>
            Masz już {existing.data.length === 1 ? 'dopasowaną wersję' : `${existing.data.length} dopasowane wersje`} do tej
            oferty:{' '}
            {existing.data.map((it, i) => (
              <span key={it.id}>
                {i > 0 && ', '}
                <strong>{it.name}</strong> (wynik {it.scoreBefore} → {it.scoreAfter}
                {it.cvExists && (
                  <>
                    , <a href={docxUrl(it.cvVariantId)} download>pobierz DOCX<span className="sr-only"> wersji {it.name}</span></a>
                  </>
                )}
                )
              </span>
            ))}
            .
          </span>
        </Alert>
      )}

      <div className="stack">
        <Card title="Ustawienia">
          {cvs.error && <ErrorBox message={cvs.error} onRetry={cvs.reload} />}
          {cvs.data && cvs.data.length === 0 ? (
            <EmptyState title="Brak CV">
              <p>
                Najpierw dodaj CV w <Link to="/profil">profilu</Link> — tailoring przestawia i wyróżnia to, co już w nim jest.
              </p>
            </EmptyState>
          ) : (
            <form onSubmit={generate} className="stack">
              <div className="tailoring-options">
                <Field label="CV bazowe" hint="Wersja, z której powstanie dopasowane CV.">
                  {(id, describedBy) => (
                    <select
                      id={id}
                      aria-describedby={describedBy}
                      value={baseCvId ?? ''}
                      onChange={(e) => setBaseCvId(Number(e.target.value))}
                    >
                      {(cvs.data ?? []).map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                          {c.id === profile.data?.activeCvId ? ' (aktywna)' : ''}
                          {isTailoredCv(c) ? ' (dopasowana)' : ''}
                        </option>
                      ))}
                    </select>
                  )}
                </Field>
                <Field label="Maks. punktów na stanowisko" hint="Puste = bez limitu. Najtrafniejsze zostają.">
                  {(id, describedBy) => (
                    <input
                      id={id}
                      aria-describedby={describedBy}
                      type="number"
                      min={1}
                      max={20}
                      inputMode="numeric"
                      value={maxBullets}
                      onChange={(e) => setMaxBullets(e.target.value)}
                    />
                  )}
                </Field>
                <div className="stack" style={{ gap: '0.5rem' }}>
                  <label className="tailoring-check">
                    <input type="checkbox" checked={hide} onChange={(e) => setHide(e.target.checked)} />
                    <span>
                      Zwiń punkty niezwiązane z wymaganiami
                      <br />
                      <small className="muted">Stanowiska i daty zawsze zostają; na stanowisko zostaje min. 1 punkt.</small>
                    </span>
                  </label>
                  <label className="tailoring-check">
                    <input
                      type="checkbox"
                      checked={useAi && aiAvailable}
                      disabled={!aiAvailable}
                      onChange={(e) => setUseAi(e.target.checked)}
                      aria-describedby="tailoring-ai-hint"
                    />
                    <span>
                      Przeredaguj podsumowanie i punkty z AI
                      <br />
                      <small className="muted" id="tailoring-ai-hint">
                        {aiAvailable
                          ? 'Model tylko redaguje (dane osobowe maskowane); każde zdanie sprawdza strażnik, odrzucone wracają do wersji offline.'
                          : `AI niedostępne${ai.data?.reason ? `: ${ai.data.reason}` : ''}. Działa wersja offline.`}
                      </small>
                    </span>
                  </label>
                </div>
              </div>
              <div className="form-actions">
                <button type="submit" className="btn btn-primary" disabled={busy !== null || baseCvId == null}>
                  {busy === 'preview' ? 'Generuję…' : preview ? 'Generuj ponownie' : 'Generuj podgląd'}
                </button>
              </div>
            </form>
          )}
        </Card>

        {error && <ErrorBox message={error} />}
        {busy === 'preview' && <Loading label="Generuję dopasowane CV…" />}

        {preview && (
          <Card
            title="Podgląd"
            actions={
              <span className="row" style={{ gap: '0.4rem' }}>
                <SourceBadge source={preview.source} />
                {edited && <Badge tone="info">edytowane</Badge>}
              </span>
            }
          >
            {preview.aiNote && <p className="small muted">{preview.aiNote}</p>}
            <Tabs
              label="Widok dopasowanego CV"
              active={tab}
              onChange={setTab}
              tabs={[
                { id: 'cv', label: 'Dopasowane CV' },
                { id: 'changes', label: 'Zmiany' },
                { id: 'control', label: `Kontrola${report && report.violations.length > 0 ? ` (${report.violations.length})` : ''}` },
              ]}
            >
              {tab === 'cv' && (
                <div className="stack">
                  {preview.competences.length > 0 && (
                    <p className="small" style={{ margin: 0 }}>
                      Kluczowe kompetencje pod to stanowisko: <strong>{preview.competences.join(', ')}</strong>
                    </p>
                  )}
                  {preview.addedFromProfile.length > 0 && (
                    <p className="small" style={{ margin: 0 }}>
                      Dopisane z profilu (nie było ich w CV bazowym): <strong>{preview.addedFromProfile.join(', ')}</strong>.
                    </p>
                  )}
                  <Field
                    label="Treść dopasowanego CV"
                    hint="Możesz poprawić tekst przed zapisem — edycja też przechodzi przez strażnika prawdomówności."
                  >
                    {(id, describedBy) => (
                      <textarea
                        id={id}
                        aria-describedby={describedBy}
                        className="tall tailoring-cv-text"
                        value={content}
                        onChange={(e) => {
                          setContent(e.target.value);
                          setAccept(false);
                        }}
                        spellCheck
                      />
                    )}
                  </Field>
                  <div className="form-actions">
                    <button type="button" className="btn" onClick={() => runCheck(true)} disabled={!edited || busy !== null}>
                      {busy === 'check' ? 'Sprawdzam…' : 'Sprawdź zmiany'}
                    </button>
                    <button
                      type="button"
                      className="btn"
                      onClick={() => {
                        setContent(preview.content);
                        setAnnounce('Przywrócono wygenerowaną wersję.');
                      }}
                      disabled={!edited}
                    >
                      Przywróć wygenerowaną wersję
                    </button>
                  </div>
                </div>
              )}
              {tab === 'changes' && (
                <div className="stack">
                  {edited && !currentCheck && (
                    <Alert tone="info">
                      <span>Pokazuję zmiany wygenerowanej wersji. Kliknij „Sprawdź zmiany”, aby uwzględnić Twoją edycję.</span>
                    </Alert>
                  )}
                  <h3>Linie CV</h3>
                  <DiffView entries={diff ?? preview.diff} />
                  <h3>Kolejność punktów doświadczenia</h3>
                  <RoleChangesView roles={preview.roles} />
                </div>
              )}
              {tab === 'control' && (
                <div className="stack">
                  {edited && !currentCheck ? (
                    <Alert tone="info">
                      <span>Tekst został zmieniony po wygenerowaniu.</span>
                      <button type="button" className="btn btn-small" onClick={() => runCheck(false)} disabled={busy !== null}>
                        Sprawdź zmiany
                      </button>
                    </Alert>
                  ) : null}
                  <h3>Wynik przed i po</h3>
                  <ScoreComparisonView comparison={comparison ?? preview.comparison} />
                  <h3>Strażnik prawdomówności</h3>
                  <GuardReportView report={report ?? preview.guard} rejected={preview.aiRejected} />
                </div>
              )}
            </Tabs>
          </Card>
        )}

        {preview && !saved && (
          <Card title="Zapisz jako nową wersję CV">
            <form onSubmit={save} className="stack">
              <Field label="Nazwa wersji">
                {(id) => <input id={id} type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={200} />}
              </Field>
              <label className="tailoring-check">
                <input type="checkbox" checked={activate} onChange={(e) => setActivate(e.target.checked)} />
                Ustaw jako aktywną wersję CV (domyślną przy aplikowaniu i dopasowaniu)
              </label>
              {hasViolations && (
                <Alert tone="warning">
                  <span>
                    Strażnik zgłosił naruszenia (zakładka „Kontrola”). Zapis jest możliwy tylko po jawnym potwierdzeniu.
                  </span>
                </Alert>
              )}
              {hasViolations && (
                <label className="tailoring-check">
                  <input type="checkbox" checked={accept} onChange={(e) => setAccept(e.target.checked)} />
                  Rozumiem — zapisz mimo naruszeń zgłoszonych przez strażnika (biorę odpowiedzialność za treść)
                </label>
              )}
              <div className="form-actions">
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={busy !== null || content.trim() === '' || (hasViolations && !accept)}
                >
                  {busy === 'save' ? 'Zapisuję…' : 'Zapisz jako nową wersję CV'}
                </button>
              </div>
            </form>
          </Card>
        )}

        {saved && (
          <Card title="Zapisano dopasowaną wersję" className="tailoring-saved">
            <p>
              Wersja <strong>{saved.item.name}</strong> jest w <Link to="/profil">profilu</Link>
              {saved.item.active ? ' i jest teraz aktywna' : ''}. Wynik dopasowania: {saved.item.scoreBefore} →{' '}
              {saved.item.scoreAfter}.
            </p>
            {!saved.item.active && (
              <p className="small muted">
                Aplikując, wybierz tę wersję w karcie „Dopasowanie do CV” na stronie oferty albo ustaw ją jako aktywną w profilu.
              </p>
            )}
            <div className="actions">
              <Link className="btn btn-primary" to={`/oferty/${offerId}`}>
                Aplikuj z tą wersją
              </Link>
              <a className="btn" href={docxUrl(saved.item.cvVariantId)} download>
                Pobierz DOCX
              </a>
              <Link className="btn" to="/dopasuj-cv">
                Biblioteka dopasowanych CV
              </Link>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}

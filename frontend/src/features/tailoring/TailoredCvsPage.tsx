import { Fragment, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { APPLICATION_STATUS, OFFER_STATUS, formatDate } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import {
  Badge, Card, EmptyState, ErrorBox, LiveMessage, Loading, PageHeader, ScoreBadge, SourceBadge,
} from '../../components/ui';
import { docxUrl, tailoringApi } from './api';
import { formatDelta } from './logic';
import type { LibraryItem } from './types';
import { DiffView, GuardReportView, ScoreComparisonView } from './TailoringViews';
import './tailoring.css';

const OFFERS_SHOWN = 8;

/** Biblioteka dopasowanych CV (#94) i wybór oferty do dopasowania. */
export function TailoredCvsPage() {
  useDocumentTitle('Tailoring CV');
  const items = useAsync(() => tailoringApi.list(), []);
  const offers = useAsync(() => tailoringApi.offers(), []);
  const [showAllOffers, setShowAllOffers] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();

  async function remove(item: LibraryItem) {
    const ok = window.confirm(
      `Usunąć „${item.name}” z biblioteki? Wersja CV zniknie też z profilu, jeśli nie jest aktywna i nie wysłano jej w aplikacji.`,
    );
    if (!ok) return;
    setError(undefined);
    try {
      const result = await tailoringApi.remove(item.cvVariantId);
      setMessage(result.message);
      if (openId === item.cvVariantId) setOpenId(null);
      items.reload();
      offers.reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const offerList = offers.data ?? [];
  const visibleOffers = showAllOffers ? offerList : offerList.slice(0, OFFERS_SHOWN);

  return (
    <>
      <PageHeader
        title="Tailoring CV"
        subtitle="Prawdziwe wersje CV dopasowane do konkretnych ofert — z kontrolą prawdomówności, wynikiem przed i po oraz powiązaniem z aplikacjami."
      />
      <LiveMessage message={message} />
      {error && <ErrorBox message={error} />}

      <div className="stack">
        <Card title="Dopasuj CV do oferty">
          {offers.error && <ErrorBox message={offers.error} onRetry={offers.reload} />}
          {offers.loading && !offers.data && <Loading />}
          {offers.data && offerList.length === 0 && (
            <EmptyState title="Brak nowych ani zapisanych ofert">
              <p>
                <Link to="/oferty/nowa">Dodaj ofertę</Link> albo pobierz oferty w <Link to="/agregator">agregatorze</Link>.
              </p>
            </EmptyState>
          )}
          {offerList.length > 0 && (
            <>
              <p className="small muted">Oferty nowe i zapisane, od najlepiej dopasowanej do aktywnego CV.</p>
              <div className="table-wrap">
                <table>
                  <caption className="sr-only">Oferty do dopasowania CV</caption>
                  <thead>
                    <tr>
                      <th scope="col">Oferta</th>
                      <th scope="col">Status</th>
                      <th scope="col">Dopasowanie</th>
                      <th scope="col">Dopasowane wersje</th>
                      <th scope="col">
                        <span className="sr-only">Akcja</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleOffers.map((o) => (
                      <tr key={o.id}>
                        <th scope="row" style={{ textTransform: 'none', letterSpacing: 0, fontSize: '0.95rem', color: 'var(--text)' }}>
                          <Link to={`/oferty/${o.id}`}>{o.title}</Link>
                          {o.company && <span className="muted small"> · {o.company}</span>}
                        </th>
                        <td>{OFFER_STATUS[o.status]}</td>
                        <td>
                          <ScoreBadge score={o.score} />
                        </td>
                        <td>{o.tailoredCount}</td>
                        <td>
                          <Link className="btn btn-small" to={`/dopasuj-cv/${o.id}`}>
                            Dopasuj CV<span className="sr-only"> do oferty {o.title}</span>
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {offerList.length > OFFERS_SHOWN && (
                <button
                  type="button"
                  className="btn btn-link"
                  aria-expanded={showAllOffers}
                  onClick={() => setShowAllOffers((v) => !v)}
                >
                  {showAllOffers ? 'Pokaż mniej' : `Pokaż wszystkie (${offerList.length})`}
                </button>
              )}
            </>
          )}
        </Card>

        <Card title="Biblioteka dopasowanych CV">
          {items.error && <ErrorBox message={items.error} onRetry={items.reload} />}
          {items.loading && !items.data && <Loading />}
          {items.data && items.data.length === 0 && (
            <EmptyState title="Nie masz jeszcze dopasowanych CV">
              <p>Wybierz ofertę powyżej i wygeneruj wersję CV pod jej wymagania.</p>
            </EmptyState>
          )}
          {items.data && items.data.length > 0 && (
            <div className="table-wrap">
              <table className="tailoring-table">
                <caption className="sr-only">Dopasowane wersje CV z ofertami i aplikacjami</caption>
                <thead>
                  <tr>
                    <th scope="col">Wersja CV</th>
                    <th scope="col">Oferta</th>
                    <th scope="col">Wynik przed → po</th>
                    <th scope="col">Źródło</th>
                    <th scope="col">Utworzono</th>
                    <th scope="col">Aplikacje</th>
                    <th scope="col">Akcje</th>
                  </tr>
                </thead>
                <tbody>
                  {items.data.map((it) => (
                    <Fragment key={it.id}>
                      <tr>
                        <td>
                          {it.cvExists ? <Link to="/profil">{it.name}</Link> : <span className="muted">{it.name}</span>}
                          {it.baseCvName && <div className="small muted">na bazie: {it.baseCvName}</div>}
                          <div className="badges">
                            {it.active && <Badge tone="success">aktywna</Badge>}
                            {it.edited && <Badge tone="info">edytowana</Badge>}
                            {it.acceptedViolations && (
                              <Badge tone="warning">zapisana mimo naruszeń ({it.violations})</Badge>
                            )}
                          </div>
                        </td>
                        <td>
                          {it.offer.exists ? (
                            <Link to={`/oferty/${it.offer.id}`}>{it.offer.title}</Link>
                          ) : (
                            <span>
                              {it.offer.title} <span className="muted small">(oferta usunięta)</span>
                            </span>
                          )}
                          {it.offer.company && <div className="small muted">{it.offer.company}</div>}
                        </td>
                        <td className="nowrap">
                          {it.scoreBefore} → {it.scoreAfter}{' '}
                          <Badge tone={it.scoreAfter > it.scoreBefore ? 'success' : 'neutral'}>
                            {formatDelta(it.scoreAfter - it.scoreBefore)}
                          </Badge>
                        </td>
                        <td>
                          <SourceBadge source={it.source} />
                        </td>
                        <td className="nowrap">{formatDate(it.createdAt)}</td>
                        <td>
                          {it.applications.length === 0 ? (
                            <span className="small">
                              brak
                              {it.offer.exists && (
                                <>
                                  {' · '}
                                  <Link to={`/oferty/${it.offer.id}`}>
                                    aplikuj<span className="sr-only"> na ofertę {it.offer.title}</span>
                                  </Link>
                                </>
                              )}
                            </span>
                          ) : (
                            <ul className="list-plain small">
                              {it.applications.map((a) => (
                                <li key={a.id} style={{ padding: '0.15rem 0', border: 'none' }}>
                                  <Link to={`/aplikacje/${a.id}`}>{APPLICATION_STATUS[a.status]}</Link>
                                  {a.appliedAt && ` · ${formatDate(a.appliedAt)}`}
                                  <div className="muted">
                                    {a.usesThisVersion ? 'z tą wersją CV' : `inna wersja: ${a.cvVariantName ?? '—'}`}
                                  </div>
                                </li>
                              ))}
                            </ul>
                          )}
                        </td>
                        <td>
                          <div className="actions">
                            <button
                              type="button"
                              className="btn btn-small"
                              aria-expanded={openId === it.cvVariantId}
                              aria-controls={`tailoring-detail-${it.cvVariantId}`}
                              onClick={() => setOpenId(openId === it.cvVariantId ? null : it.cvVariantId)}
                            >
                              {openId === it.cvVariantId ? 'Ukryj' : 'Szczegóły'}
                              <span className="sr-only"> wersji {it.name}</span>
                            </button>
                            {it.cvExists && (
                              <a className="btn btn-small" href={docxUrl(it.cvVariantId)} download>
                                DOCX<span className="sr-only"> — pobierz wersję {it.name}</span>
                              </a>
                            )}
                            <button type="button" className="btn btn-small btn-danger" onClick={() => remove(it)}>
                              Usuń<span className="sr-only"> wersję {it.name}</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                      {openId === it.cvVariantId && (
                        <tr>
                          <td colSpan={7} id={`tailoring-detail-${it.cvVariantId}`}>
                            <DetailPanel cvVariantId={it.cvVariantId} />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

/** Szczegóły zapisanej wersji: treść, aktualny wynik przed/po, raport strażnika i różnice. */
function DetailPanel({ cvVariantId }: { cvVariantId: number }) {
  const detail = useAsync(() => tailoringApi.detail(cvVariantId), [cvVariantId]);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.error) return <ErrorBox message={detail.error} onRetry={detail.reload} />;
  const d = detail.data!;
  return (
    <div className="tailoring-detail stack">
      {d.content == null ? (
        <p className="muted">Wersja CV została usunięta z profilu.</p>
      ) : (
        <details>
          <summary className="label">Treść wersji</summary>
          <pre className="tailoring-cv-view" tabIndex={0} aria-label={`Treść wersji ${d.item.name}`}>
            {d.content}
          </pre>
        </details>
      )}
      {d.comparison && (
        <details>
          <summary className="label">Wynik przed i po (stan bieżący)</summary>
          <ScoreComparisonView comparison={d.comparison} />
        </details>
      )}
      <details open={d.guard.violations.length > 0}>
        <summary className="label">Strażnik prawdomówności</summary>
        <GuardReportView report={d.guard} />
      </details>
      {d.diff.length > 0 && (
        <details>
          <summary className="label">Zmiany względem CV bazowego</summary>
          <DiffView entries={d.diff} />
        </details>
      )}
    </div>
  );
}

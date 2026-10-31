import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDate, todayIso } from '../../api/labels';
import { Card, EmptyState, ErrorBox, Field, Loading, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { analyticsApi } from './api';
import { defaultReportRange, formatReportDateTime, isValidRange } from './logic';
import type { ActivityReport } from './types';
import './analytics.css';

/** Raport aktywności poszukiwania pracy (#79): tabela, wydruk / PDF i CSV. */
export function ReportPage() {
  useDocumentTitle('Raport aktywności');
  const initial = defaultReportRange(todayIso());
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const valid = Boolean(from && to) && isValidRange(from, to);
  const report = useAsync(
    () => (valid ? analyticsApi.report(from, to) : Promise.resolve(undefined)),
    [from, to, valid],
  );
  const r = report.data;

  return (
    <div className="analytics-report">
      <PageHeader
        title="Raport aktywności poszukiwania pracy"
        subtitle={
          <>
            {r?.candidateName && <strong>{r.candidateName}</strong>}
            {r?.candidateName && ' · '}
            Okres: {formatDate(r?.from ?? from)} – {formatDate(r?.to ?? to)}
          </>
        }
        actions={
          <div className="actions no-print">
            <button type="button" className="btn btn-primary" onClick={() => window.print()} disabled={!r}>
              Drukuj / zapisz jako PDF
            </button>
            {valid ? (
              <a className="btn" href={analyticsApi.reportCsvUrl(from, to)} download>Pobierz CSV</a>
            ) : (
              <button type="button" className="btn" disabled>Pobierz CSV</button>
            )}
          </div>
        }
      />

      <Card className="no-print" title="Zakres dat">
        <div className="analytics-filters">
          <Field label="Od">
            {(id) => <input id={id} type="date" value={from} required onChange={(e) => setFrom(e.target.value)} />}
          </Field>
          <Field label="Do" error={valid ? undefined : 'Podaj obie daty; „do” nie może być wcześniejsze niż „od”.'}>
            {(id, describedBy) => (
              <input id={id} type="date" value={to} required aria-invalid={!valid} aria-describedby={describedBy}
                onChange={(e) => setTo(e.target.value)} />
            )}
          </Field>
        </div>
        <p className="small muted">
          CSV ma kodowanie UTF-8 i separator „;” — otwiera się poprawnie w Excelu z polskimi ustawieniami.
          Wydruk zawiera tylko raport (bez menu i przycisków). <Link to="/analityka">Wróć do analityki</Link>
        </p>
      </Card>

      {report.loading && !r && <Loading />}
      {report.error && <ErrorBox message={report.error} onRetry={report.reload} />}
      {r && <ReportBody r={r} />}
    </div>
  );
}

function ReportBody({ r }: { r: ActivityReport }) {
  const s = r.summary;
  const stats: [number, string][] = [
    [s.applicationsSent, 'wysłane aplikacje'],
    [s.distinctCompanies, 'firmy'],
    [s.responses, 'odpowiedzi firm'],
    [s.interviews, 'rozmowy rekrutacyjne'],
    [s.offers, 'otrzymane oferty'],
    [s.followUpsSent, 'wysłane follow-upy'],
  ];
  return (
    <div className="stack" style={{ marginTop: '1rem' }}>
      <Card title="Podsumowanie">
        <div className="grid-3">
          {stats.map(([value, label]) => (
            <div key={label} className="stat">
              <span className="stat-value">{value}</span>
              <span className="stat-label">{label}</span>
            </div>
          ))}
        </div>
      </Card>

      <Card title={`Aplikacje (${r.applications.length})`}>
        {r.applications.length === 0 ? (
          <EmptyState title="Brak wysłanych aplikacji w tym okresie" />
        ) : (
          <div className="table-wrap">
            <table className="analytics-table">
              <caption className="sr-only">Wysłane aplikacje w wybranym okresie</caption>
              <thead>
                <tr>
                  <th scope="col">Data</th>
                  <th scope="col">Firma</th>
                  <th scope="col">Stanowisko</th>
                  <th scope="col">Źródło</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {r.applications.map((a) => (
                  <tr key={a.applicationId}>
                    <td className="nowrap">{formatDate(a.date)}</td>
                    <th scope="row">{a.company}</th>
                    <td><Link to={`/aplikacje/${a.applicationId}`}>{a.position}</Link></td>
                    <td>{a.source ?? '—'}</td>
                    <td>{a.statusLabel}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title={`Rozmowy (${r.interviews.length})`}>
        {r.interviews.length === 0 ? (
          <EmptyState title="Brak rozmów w tym okresie" />
        ) : (
          <div className="table-wrap">
            <table className="analytics-table">
              <caption className="sr-only">Rozmowy rekrutacyjne w wybranym okresie</caption>
              <thead>
                <tr>
                  <th scope="col">Data</th>
                  <th scope="col">Firma</th>
                  <th scope="col">Stanowisko</th>
                  <th scope="col">Rodzaj</th>
                </tr>
              </thead>
              <tbody>
                {r.interviews.map((i) => (
                  <tr key={i.interviewId}>
                    <td className="nowrap">{formatReportDateTime(i.date)}</td>
                    <th scope="row">{i.company}</th>
                    <td>{i.position}</td>
                    <td>{i.kindLabel}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <p className="small muted">Wygenerowano: {formatReportDateTime(r.generatedAt)} · Radar Pracy</p>
    </div>
  );
}

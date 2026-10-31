import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { MatchResult } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Card, ScoreRing } from '../../components/ui';
import { docxUrl, tailoringApi } from './api';
import { latestForOffer } from './logic';
import './tailoring.css';

/** Gniazdo modułu „tailoring” na stronie oferty: wejście do generatora dopasowanego CV. */
export function TailorCvCallout({ offerId, cvId }: { offerId: number; cvId: number | null }) {
  const match = useAsync(
    () => api.get<MatchResult>(`/api/offers/${offerId}/match${cvId ? `?cvId=${cvId}` : ''}`),
    [offerId, cvId],
  );
  const existing = useAsync(() => tailoringApi.list(offerId), [offerId]);
  const latest = existing.data ? latestForOffer(existing.data, offerId) : undefined;
  const target = `/dopasuj-cv/${offerId}${cvId ? `?cvId=${cvId}` : ''}`;

  return (
    <Card title="Dopasuj CV do tej oferty" className="tailoring-callout no-print">
      <div className="row" style={{ alignItems: 'flex-start' }}>
        {match.data && <ScoreRing score={match.data.score} size={56} label="Obecne dopasowanie" />}
        <div style={{ flex: 1, minWidth: 220 }}>
          <p>
            {match.data ? (
              <>
                Obecne dopasowanie{match.data.cvName ? ` CV „${match.data.cvName}”` : ''}: <strong>{match.data.score}/100</strong>.{' '}
              </>
            ) : null}
            Wygeneruj wersję CV pod tę ofertę: kluczowe kompetencje z wymagań i najtrafniejsze punkty doświadczenia na górze.
            Strażnik pilnuje, by nie pojawiło się nic, czego nie ma w CV ani w profilu.
          </p>
          {latest && (
            <p className="small">
              Masz już dopasowaną wersję <strong>{latest.name}</strong> (wynik {latest.scoreBefore} → {latest.scoreAfter}).{' '}
              <a href={docxUrl(latest.cvVariantId)} download>
                Pobierz DOCX<span className="sr-only"> wersji {latest.name}</span>
              </a>
              {' · '}
              <Link to="/dopasuj-cv">Biblioteka dopasowanych CV</Link>
            </p>
          )}
          <Link className="btn btn-primary" to={target}>
            {latest ? 'Dopasuj ponownie' : 'Dopasuj CV'}
          </Link>
        </div>
      </div>
    </Card>
  );
}

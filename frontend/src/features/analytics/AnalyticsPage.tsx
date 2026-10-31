import { Link } from 'react-router-dom';
import { Card, PageHeader } from '../../components/ui';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { EffectivenessCard } from './EffectivenessCard';
import { FunnelCard } from './FunnelCard';
import { MoodHistoryCard } from './MoodHistoryCard';
import { SkillGapsCard } from './SkillGapsCard';
import { SkillTrendsCard } from './SkillTrendsCard';
import './analytics.css';

/** Analityka: lejek (#71), skuteczność (#72), luki (#30), trendy (#75), nastrój (#76), raport (#79). */
export function AnalyticsPage() {
  useDocumentTitle('Analityka');
  return (
    <>
      <PageHeader
        title="Analityka"
        subtitle="Co działa w Twoich poszukiwaniach, czego szuka rynek i jak się czujesz. Wszystkie liczby liczy kod — bez AI."
        actions={<Link className="btn btn-primary" to="/raport">Raport aktywności</Link>}
      />
      <div className="stack">
        <div className="grid-2">
          <FunnelCard />
          <SkillTrendsCard />
        </div>
        <EffectivenessCard />
        <SkillGapsCard />
        <MoodHistoryCard />
        <div>
          <Card title="Raport aktywności">
            <p>
              Zestawienie wysłanych aplikacji i rozmów w wybranym okresie — do wydruku, zapisania jako PDF lub
              pobrania w CSV, np. dla doradcy kariery albo urzędu pracy.
            </p>
            <Link className="btn" to="/raport">Przejdź do raportu</Link>
          </Card>
        </div>
      </div>
    </>
  );
}

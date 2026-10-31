import { useSearchParams } from 'react-router-dom';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';
import { NetCalculator } from './NetCalculator';
import { OfferComparison } from './OfferComparison';
import { SalaryStatsView } from './SalaryStatsView';
import { NegotiationAssistant } from './NegotiationAssistant';
import { SalaryTabs, panelId, tabId } from './SalaryTabs';
import { SALARY_TABS, SALARY_TAB_LABEL, parseContractKind, parseTab, type SalaryTab } from './salaryUtils';
import './salary.css';

/** Narzędzia płacowe: kalkulator netto (#63), porównywarka (#64), statystyki (#65), negocjacje (#66). */
export function SalaryPage() {
  useDocumentTitle('Wynagrodzenia');
  const [params, setParams] = useSearchParams();
  const tab = parseTab(params.get('tab'));
  const offerParam = Number(params.get('offerId'));
  const offerId = Number.isFinite(offerParam) && offerParam > 0 ? offerParam : null;

  function select(next: SalaryTab) {
    const p = new URLSearchParams(params);
    p.set('tab', next);
    setParams(p, { replace: true });
  }

  function selectOffer(id: number | null) {
    const p = new URLSearchParams(params);
    if (id) p.set('offerId', String(id));
    else p.delete('offerId');
    setParams(p, { replace: true });
  }

  return (
    <>
      <PageHeader
        title="Wynagrodzenia"
        subtitle="Ile zostanie na rękę, jak oferty wypadają na tle rynku i jak negocjować. Kwoty liczy aplikacja — to szacunki, nie porada podatkowa."
      />
      <SalaryTabs tabs={SALARY_TABS} labels={SALARY_TAB_LABEL} active={tab} onChange={select} prefix="salary" label="Narzędzia płacowe" />
      <div role="tabpanel" id={panelId('salary', tab)} aria-labelledby={tabId('salary', tab)}>
        {tab === 'kalkulator' && (
          <NetCalculator
            key={`${params.get('amount')}-${params.get('contract')}`}
            initialAmount={params.get('amount')}
            initialContract={parseContractKind(params.get('contract'))}
          />
        )}
        {tab === 'porownanie' && <OfferComparison />}
        {tab === 'statystyki' && <SalaryStatsView />}
        {tab === 'negocjacje' && <NegotiationAssistant offerId={offerId} onOfferChange={selectOffer} />}
      </div>
    </>
  );
}

import { api } from '../../api/client';
import type { ContractType, Seniority } from '../../api/types';
import type {
  CompareRequest, Comparison, NegotiationPlan, NetRequest, NetResult, SalaryBenchmark, SalaryStats, TaxParameters,
} from './types';

/** Wywołania API modułu „wynagrodzenia”. */
export const salaryApi = {
  net: (req: NetRequest) => api.post<NetResult>('/api/salary/net', req),
  taxParameters: () => api.get<TaxParameters>('/api/salary/tax-parameters'),
  compare: (req: CompareRequest) => api.post<Comparison>('/api/salary/compare', req),
  stats: (seniority?: Seniority | '', contract?: ContractType | '') => {
    const params = new URLSearchParams();
    if (seniority) params.set('seniority', seniority);
    if (contract) params.set('contract', contract);
    const q = params.toString();
    return api.get<SalaryStats>(`/api/salary/stats${q ? `?${q}` : ''}`);
  },
  negotiation: (offerId: number) => api.get<NegotiationPlan>(`/api/offers/${offerId}/negotiation`),
  benchmark: (offerId: number) => api.get<SalaryBenchmark>(`/api/offers/${offerId}/salary-benchmark`),
};

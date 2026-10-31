import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { JobOffer, JobOfferDraft } from '../../api/types';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, PageHeader } from '../../components/ui';
import { OfferForm } from '../../components/OfferForm';

/** Dodawanie oferty (wersja bazowa; parser i import z URL dodaje moduł „oferty”). */
export function OfferNewPage() {
  useDocumentTitle('Nowa oferta');
  const navigate = useNavigate();

  async function create(draft: JobOfferDraft) {
    const saved = await api.post<JobOffer>('/api/offers', draft);
    navigate(`/oferty/${saved.id}`);
  }

  return (
    <>
      <PageHeader title="Nowa oferta" subtitle="Wklej treść ogłoszenia — wymagania zostaną wyodrębnione automatycznie." />
      <Card>
        <OfferForm onSubmit={create} submitLabel="Dodaj ofertę" onCancel={() => navigate('/oferty')} />
      </Card>
    </>
  );
}

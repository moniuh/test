import { Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProfilePage } from './pages/ProfilePage';
import { OfferDetailPage } from './pages/OfferDetailPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { DashboardPage } from './features/analytics/DashboardPage';
import { AnalyticsPage } from './features/analytics/AnalyticsPage';
import { ReportPage } from './features/analytics/ReportPage';
import { OffersPage } from './features/matching/OffersPage';
import { OfferNewPage } from './features/offers/OfferNewPage';
import { ApplicationsPage } from './features/applications/ApplicationsPage';
import { ApplicationDetailPage } from './features/applications/ApplicationDetailPage';
import { InterviewsPage } from './features/interviews/InterviewsPage';
import { SalaryPage } from './features/salary/SalaryPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="oferty" element={<OffersPage />} />
        <Route path="oferty/nowa" element={<OfferNewPage />} />
        <Route path="oferty/:id" element={<OfferDetailPage />} />
        <Route path="aplikacje" element={<ApplicationsPage />} />
        <Route path="aplikacje/:id" element={<ApplicationDetailPage />} />
        <Route path="rozmowy" element={<InterviewsPage />} />
        <Route path="wynagrodzenia" element={<SalaryPage />} />
        <Route path="analityka" element={<AnalyticsPage />} />
        <Route path="raport" element={<ReportPage />} />
        <Route path="profil" element={<ProfilePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

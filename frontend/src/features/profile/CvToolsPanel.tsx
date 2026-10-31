import { useEffect, useId, useState } from 'react';
import type { CvVariant } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Badge, EmptyState } from '../../components/ui';
import { cvToolsApi } from './api';
import { Tabs, type TabDef } from './components';
import { CvAuditSection } from './CvAuditSection';
import { CvImportSection } from './CvImportSection';
import { CvSensitiveSection } from './CvSensitiveSection';
import { CvSkillsSection } from './CvSkillsSection';
import './profile.css';

type ToolId = 'audit' | 'skills' | 'sensitive' | 'import';

/**
 * Gniazdo modułu „profil”: import CV (#1), ekstrakcja umiejętności (#2), audyt ATS (#4),
 * dane wrażliwe (#8). `onChanged` odświeża listę wersji CV i profil.
 */
export function CvToolsPanel({ cv, onChanged }: { cv: CvVariant | null; onChanged: () => void }) {
  const idBase = useId();
  const [tab, setTab] = useState<ToolId>(cv ? 'audit' : 'import');
  // dane wrażliwe ładujemy od razu — liczba znalezisk jest widoczna na zakładce
  const sensitive = useAsync(
    () => (cv ? cvToolsApi.sensitive(cv.id) : Promise.resolve(null)),
    [cv?.id, cv?.updatedAt],
  );
  const findings = cv ? sensitive.data?.findings.length ?? 0 : 0;

  // bez żadnej wersji CV jedyną użyteczną zakładką jest import
  const hasCv = cv != null;
  useEffect(() => {
    if (!hasCv) setTab('import');
  }, [hasCv]);

  const tabs: TabDef<ToolId>[] = [
    { id: 'audit', label: 'Audyt ATS' },
    { id: 'skills', label: 'Umiejętności z CV' },
    {
      id: 'sensitive',
      label: (
        <>
          Dane wrażliwe
          {findings > 0 && (
            <span className="profile-tab-count">
              <Badge tone="warning">
                <span aria-hidden="true">⚠ </span>
                {findings}
                <span className="sr-only"> do sprawdzenia</span>
              </Badge>
            </span>
          )}
        </>
      ),
    },
    { id: 'import', label: 'Import z pliku' },
  ];

  return (
    <section className="profile-cvtools" aria-labelledby={`${idBase}-title`}>
      <h3 id={`${idBase}-title`}>
        Narzędzia CV{cv && <span className="muted small"> — wersja „{cv.name}”</span>}
      </h3>
      <p className="muted small" style={{ margin: 0 }}>
        Sprawdź CV pod kątem systemów ATS, przenieś umiejętności do profilu, usuń zbędne dane osobowe albo zaimportuj CV z
        pliku. Wyniki dotyczą zapisanej treści wybranej wersji.
      </p>
      <Tabs
        idBase={idBase}
        label="Narzędzia CV"
        tabs={tabs}
        active={tab}
        onChange={setTab}
        renderPanel={(id) => {
          if (id === 'import') return <CvImportSection onImported={onChanged} />;
          if (!cv) {
            return (
              <EmptyState title="Nie wybrano wersji CV">
                <p className="small">Utwórz wersję CV powyżej albo zaimportuj ją z pliku PDF lub DOCX.</p>
              </EmptyState>
            );
          }
          if (id === 'audit') return <CvAuditSection cv={cv} />;
          if (id === 'skills') return <CvSkillsSection cv={cv} onChanged={onChanged} />;
          return <CvSensitiveSection report={sensitive} />;
        }}
      />
    </section>
  );
}

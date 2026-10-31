import { useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { CandidateProfile, ContractType, CvVariant, SkillEntry, WorkMode } from '../api/types';
import { CONTRACT, WORK_MODE, formatDate } from '../api/labels';
import { useAsync } from '../hooks/useAsync';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { Badge, Card, ErrorBox, Field, LiveMessage, Loading, PageHeader } from '../components/ui';
import { CvToolsPanel } from '../features/profile/CvToolsPanel';

const csv = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);
const lines = (s: string) => s.split('\n').map((x) => x.trim()).filter(Boolean);

export function ProfilePage() {
  useDocumentTitle('Profil i CV');
  const profile = useAsync(() => api.get<CandidateProfile>('/api/profile'), []);
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const reloadAll = () => {
    profile.reload();
    cvs.reload();
  };

  return (
    <>
      <PageHeader
        title="Profil i CV"
        subtitle="Dane, preferencje i wersje CV — na ich podstawie liczone jest dopasowanie ofert."
      />
      {profile.error && <ErrorBox message={profile.error} onRetry={profile.reload} />}
      {(profile.loading && !profile.data) || (cvs.loading && !cvs.data) ? <Loading /> : null}
      {profile.data && cvs.data && (
        <div className="stack">
          <CvSection profile={profile.data} cvs={cvs.data} onChanged={reloadAll} />
          <ProfileForm profile={profile.data} onSaved={profile.setData} />
        </div>
      )}
    </>
  );
}

function CvSection({ profile, cvs, onChanged }: { profile: CandidateProfile; cvs: CvVariant[]; onChanged: () => void }) {
  const [selectedId, setSelectedId] = useState<number | null>(profile.activeCvId ?? cvs[0]?.id ?? null);
  const selected = cvs.find((c) => c.id === selectedId) ?? null;
  const [name, setName] = useState('');
  const [role, setRole] = useState('');
  const [content, setContent] = useState('');
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!cvs.find((c) => c.id === selectedId)) setSelectedId(profile.activeCvId ?? cvs[0]?.id ?? null);
  }, [cvs, profile.activeCvId, selectedId]);

  useEffect(() => {
    setName(selected?.name ?? '');
    setRole(selected?.targetRole ?? '');
    setContent(selected?.content ?? '');
    setMessage(undefined);
  }, [selected?.id, selected?.updatedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  async function run(action: () => Promise<unknown>, done: string) {
    setError(undefined);
    setMessage(undefined);
    try {
      await action();
      setMessage(done);
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    void run(() => api.put(`/api/cv/${selected.id}`, { name, targetRole: role, content }), 'Zapisano wersję CV');
  };

  const create = () =>
    run(async () => {
      const cv = await api.post<CvVariant>('/api/cv', {
        name: `Nowa wersja ${cvs.length + 1}`,
        targetRole: '',
        content: selected?.content ?? '',
      });
      setSelectedId(cv.id);
    }, 'Utworzono nową wersję CV (kopia bieżącej)');

  const remove = () => {
    if (!selected || !window.confirm(`Usunąć wersję „${selected.name}”?`)) return;
    void run(() => api.del(`/api/cv/${selected.id}`), 'Usunięto wersję CV');
  };

  return (
    <Card
      title="Wersje CV"
      actions={
        <button type="button" className="btn" onClick={create}>
          Nowa wersja
        </button>
      }
    >
      {error && <ErrorBox message={error} />}
      {cvs.length === 0 ? (
        <p className="muted">Nie masz jeszcze CV. Utwórz wersję i wklej treść albo zaimportuj plik.</p>
      ) : (
        <div className="tabs" role="tablist" aria-label="Wersje CV">
          {cvs.map((c) => (
            <button
              key={c.id}
              type="button"
              role="tab"
              aria-selected={c.id === selectedId}
              aria-controls="cv-editor"
              onClick={() => setSelectedId(c.id)}
            >
              {c.name} {c.id === profile.activeCvId && <Badge tone="success">aktywna</Badge>}
            </button>
          ))}
        </div>
      )}
      {selected && (
        <form id="cv-editor" role="tabpanel" onSubmit={save}>
          <div className="grid-2">
            <Field label="Nazwa wersji">{(id) => <input id={id} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
            <Field label="Rola docelowa">{(id) => <input id={id} value={role} onChange={(e) => setRole(e.target.value)} />}</Field>
          </div>
          <Field label="Treść CV" hint={`Źródło: ${selected.origin ?? 'ręcznie'} · zmieniono ${formatDate(selected.updatedAt)}`}>
            {(id, d) => <textarea id={id} aria-describedby={d} className="tall" value={content} onChange={(e) => setContent(e.target.value)} />}
          </Field>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary">
              Zapisz CV
            </button>
            {selected.id !== profile.activeCvId && (
              <button type="button" className="btn" onClick={() => run(() => api.post(`/api/cv/${selected.id}/activate`), 'Ustawiono jako aktywną')}>
                Ustaw jako aktywną
              </button>
            )}
            <button type="button" className="btn btn-danger" onClick={remove}>
              Usuń wersję
            </button>
          </div>
          <LiveMessage message={message} />
        </form>
      )}
      <CvToolsPanel cv={selected} onChanged={onChanged} />
    </Card>
  );
}

function ProfileForm({ profile, onSaved }: { profile: CandidateProfile; onSaved: (p: CandidateProfile) => void }) {
  const [p, setP] = useState<CandidateProfile>(profile);
  const [roles, setRoles] = useState(profile.desiredRoles.join(', '));
  const [locations, setLocations] = useState(profile.preferredLocations.join(', '));
  const [dealBreakers, setDealBreakers] = useState(profile.dealBreakers.join('\n'));
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    setP(profile);
    setRoles(profile.desiredRoles.join(', '));
    setLocations(profile.preferredLocations.join(', '));
    setDealBreakers(profile.dealBreakers.join('\n'));
  }, [profile]);

  const set = <K extends keyof CandidateProfile>(k: K, v: CandidateProfile[K]) => setP((x) => ({ ...x, [k]: v }));
  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  const setSkill = (i: number, patch: Partial<SkillEntry>) =>
    set('skills', p.skills.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));
  const num = (v: string) => (v === '' ? null : Number(v));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(undefined);
    setMessage(undefined);
    try {
      const saved = await api.put<CandidateProfile>('/api/profile', {
        ...p,
        desiredRoles: csv(roles),
        preferredLocations: csv(locations),
        dealBreakers: lines(dealBreakers),
        skills: p.skills.filter((s) => s.name.trim()),
      });
      onSaved(saved);
      setMessage('Zapisano profil');
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <form onSubmit={submit} className="stack">
      {error && <ErrorBox message={error} />}
      <Card title="Dane kontaktowe">
        <div className="grid-2">
          <Field label="Imię i nazwisko">{(id) => <input id={id} value={p.fullName ?? ''} onChange={(e) => set('fullName', e.target.value)} autoComplete="name" />}</Field>
          <Field label="Nagłówek" hint="np. Java Developer, 4 lata doświadczenia">
            {(id, d) => <input id={id} aria-describedby={d} value={p.headline ?? ''} onChange={(e) => set('headline', e.target.value)} />}
          </Field>
          <Field label="E-mail">{(id) => <input id={id} type="email" value={p.email ?? ''} onChange={(e) => set('email', e.target.value)} autoComplete="email" />}</Field>
          <Field label="Telefon">{(id) => <input id={id} type="tel" value={p.phone ?? ''} onChange={(e) => set('phone', e.target.value)} autoComplete="tel" />}</Field>
          <Field label="Miasto">{(id) => <input id={id} value={p.location ?? ''} onChange={(e) => set('location', e.target.value)} />}</Field>
        </div>
      </Card>

      <Card title="Umiejętności" actions={<span className="muted small">Lata doświadczenia poprawiają ocenę wymagań typu „min. 3 lata”</span>}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Umiejętność</th>
                <th scope="col">Poziom (1–5)</th>
                <th scope="col">Lata</th>
                <th scope="col"><span className="sr-only">Akcje</span></th>
              </tr>
            </thead>
            <tbody>
              {p.skills.map((s, i) => (
                <tr key={i}>
                  <td>
                    <input aria-label={`Umiejętność ${i + 1}`} value={s.name} onChange={(e) => setSkill(i, { name: e.target.value })} />
                  </td>
                  <td>
                    <input aria-label={`Poziom: ${s.name}`} type="number" min={1} max={5} value={s.level ?? ''} onChange={(e) => setSkill(i, { level: num(e.target.value) })} style={{ maxWidth: '6rem' }} />
                  </td>
                  <td>
                    <input aria-label={`Lata: ${s.name}`} type="number" min={0} max={40} value={s.years ?? ''} onChange={(e) => setSkill(i, { years: num(e.target.value) })} style={{ maxWidth: '6rem' }} />
                  </td>
                  <td>
                    <button type="button" className="btn btn-small btn-danger" onClick={() => set('skills', p.skills.filter((_, idx) => idx !== i))} aria-label={`Usuń umiejętność ${s.name}`}>
                      Usuń
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button type="button" className="btn btn-small" style={{ marginTop: '0.5rem' }} onClick={() => set('skills', [...p.skills, { name: '', level: null, years: null }])}>
          Dodaj umiejętność
        </button>
      </Card>

      <Card title="Preferencje">
        <div className="grid-2">
          <Field label="Szukane stanowiska" hint="Oddzielone przecinkami">
            {(id, d) => <input id={id} aria-describedby={d} value={roles} onChange={(e) => setRoles(e.target.value)} />}
          </Field>
          <Field label="Preferowane lokalizacje" hint="Oddzielone przecinkami, np. Warszawa, zdalnie">
            {(id, d) => <input id={id} aria-describedby={d} value={locations} onChange={(e) => setLocations(e.target.value)} />}
          </Field>
          <Field label="Minimalne wynagrodzenie (zł/mies., równoważnik UoP brutto)">
            {(id) => <input id={id} type="number" min={0} value={p.minMonthlySalary ?? ''} onChange={(e) => set('minMonthlySalary', num(e.target.value))} />}
          </Field>
          <Field label="Cel: aplikacji tygodniowo">
            {(id) => <input id={id} type="number" min={0} max={100} value={p.weeklyApplicationGoal ?? ''} onChange={(e) => set('weeklyApplicationGoal', num(e.target.value))} />}
          </Field>
          <Field label="Przypomnienie o follow-upie po (dniach)">
            {(id) => <input id={id} type="number" min={1} max={60} value={p.followUpDays ?? ''} onChange={(e) => set('followUpDays', num(e.target.value))} />}
          </Field>
          <Field label="Wykluczenia (deal-breakery)" hint="Jedna fraza w linii, np. praca zmianowa">
            {(id, d) => <textarea id={id} aria-describedby={d} value={dealBreakers} onChange={(e) => setDealBreakers(e.target.value)} style={{ minHeight: 80 }} />}
          </Field>
        </div>
        <fieldset>
          <legend>Akceptowany tryb pracy</legend>
          <div className="checks">
            {(Object.keys(WORK_MODE) as WorkMode[]).map((m) => (
              <label key={m}>
                <input type="checkbox" checked={p.workModes.includes(m)} onChange={() => set('workModes', toggle(p.workModes, m))} /> {WORK_MODE[m]}
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend>Akceptowane formy zatrudnienia</legend>
          <div className="checks">
            {(Object.keys(CONTRACT) as ContractType[]).map((c) => (
              <label key={c}>
                <input type="checkbox" checked={p.contractTypes.includes(c)} onChange={() => set('contractTypes', toggle(p.contractTypes, c))} /> {CONTRACT[c]}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="row" style={{ gap: '0.4rem' }}>
          <input type="checkbox" checked={p.willingToRelocate} onChange={(e) => set('willingToRelocate', e.target.checked)} /> Rozważam przeprowadzkę
        </label>
      </Card>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary">
          Zapisz profil
        </button>
      </div>
      <LiveMessage message={message} />
    </form>
  );
}

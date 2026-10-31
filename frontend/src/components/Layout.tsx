import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useRef } from 'react';

const NAV = [
  { to: '/', label: 'Pulpit', end: true },
  { to: '/agregator', label: 'Agregator' },
  { to: '/oferty', label: 'Oferty' },
  { to: '/dopasuj-cv', label: 'Tailoring CV' },
  { to: '/aplikacje', label: 'Aplikacje' },
  { to: '/rozmowy', label: 'Rozmowy' },
  { to: '/wynagrodzenia', label: 'Wynagrodzenia' },
  { to: '/analityka', label: 'Analityka' },
  { to: '/profil', label: 'Profil i CV' },
];

export function Layout() {
  const location = useLocation();
  const mainRef = useRef<HTMLElement>(null);

  // po zmianie widoku przenieś fokus na treść (czytniki ekranu i klawiatura)
  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <>
      <a className="skip-link" href="#tresc">
        Przejdź do treści
      </a>
      <header className="app-header">
        <div className="container header-inner">
          <NavLink to="/" className="brand" aria-label="Radar Pracy — pulpit">
            <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
              <circle cx="16" cy="16" r="14" fill="currentColor" />
              <circle cx="16" cy="16" r="8" fill="none" stroke="white" strokeWidth="2" />
              <circle cx="16" cy="16" r="2.5" fill="white" />
            </svg>
            <span>Radar Pracy</span>
          </NavLink>
          <nav aria-label="Główna nawigacja">
            <ul className="nav">
              {NAV.map((n) => (
                <li key={n.to}>
                  <NavLink to={n.to} end={n.end}>
                    {n.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main id="tresc" ref={mainRef} tabIndex={-1} className="container main">
        <Outlet />
      </main>
      <footer className="container footer muted">
        Radar Pracy · dane zostają na Twoim serwerze · AI tylko po zamaskowaniu danych osobowych
      </footer>
    </>
  );
}

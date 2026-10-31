import { useEffect } from 'react';

/** Ustawia tytuł karty przeglądarki (ważne dla czytników ekranu przy zmianie widoku). */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · Radar Pracy` : 'Radar Pracy';
  }, [title]);
}

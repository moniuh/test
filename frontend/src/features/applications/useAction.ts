import { useCallback, useState } from 'react';
import { errorMessage } from '../../api/client';
import type { ApplicationView } from '../../api/types';

/** Wykonanie akcji asynchronicznej ze stanem „w toku” i komunikatem błędu. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const run = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(undefined);
    try {
      return await fn();
    } catch (e) {
      setError(errorMessage(e));
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);
  return { busy, error, run, setError };
}

/** Wywołanie zwrotne sekcji szczegółów aplikacji: nowe dane aplikacji (jeśli są) i komunikat do ogłoszenia. */
export type OnChanged = (updated?: ApplicationView, message?: string) => void;

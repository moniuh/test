import { useEffect, useState } from 'react';

/** Wartość opóźniona o `delay` ms (np. fraza wyszukiwania — bez zapytania przy każdym znaku). */
export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

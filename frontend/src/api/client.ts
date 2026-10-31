/** Klient REST: jednolita obsługa błędów `{ error, message, details }` z backendu. */

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly details: string[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function parseError(res: Response): Promise<ApiError> {
  let message = `Błąd ${res.status}`;
  let details: string[] = [];
  try {
    const body = await res.json();
    if (body?.message) message = body.message;
    if (Array.isArray(body?.details)) details = body.details;
  } catch {
    // odpowiedź bez JSON
  }
  return new ApiError(message, res.status, details);
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const init: RequestInit = { method, headers: { Accept: 'application/json' } };
  if (body instanceof FormData) {
    init.body = body;
  } else if (body !== undefined) {
    init.headers = { ...init.headers, 'Content-Type': 'application/json' };
    init.body = JSON.stringify(body);
  }
  let res: Response;
  try {
    res = await fetch(path, init);
  } catch {
    throw new ApiError('Brak połączenia z serwerem — czy backend działa?', 0);
  }
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('Content-Type') ?? '';
  if (type.includes('application/json')) return (await res.json()) as T;
  return (await res.text()) as unknown as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  del: (path: string) => request<void>('DELETE', path),
  /** Wysyłka pliku (multipart/form-data). */
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, form),
};

/** Komunikat błędu do wyświetlenia użytkownikowi. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.details.length ? `${e.message}: ${e.details.join(', ')}` : e.message;
  if (e instanceof Error) return e.message;
  return 'Nieznany błąd';
}

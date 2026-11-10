import type { FieldError } from '../trips/types';
const apiBase = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1').replace(/\/$/, '');
export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly fields: FieldError[] = []) { super(message); }
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${apiBase}${path}`, {
      ...options,
      signal: options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(10000)]) : AbortSignal.timeout(10000),
      headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError('Cannot reach the API. Check the backend and retry. Your form has been preserved.', 0);
  }
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const payload = body as { message?: string; fieldErrors?: FieldError[] } | null;
    throw new ApiError(payload?.message ?? `Request failed (${response.status})`, response.status, payload?.fieldErrors ?? []);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

'use client';

import { ApiError } from './errors';
import { tokenStore } from './tokenStore';
import type { ApiResponse } from './types';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Multipart payload. When set, {@link RequestOptions.body} is ignored. */
  formData?: FormData;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
  /** Skip the Authorization header - used by the auth endpoints themselves. */
  anonymous?: boolean;
  /** Internal: set while replaying a request after a token refresh. */
  isRetry?: boolean;
}

/**
 * The single HTTP entry point for the whole app.
 *
 * Responsibilities, all in one place so no component has to think about them:
 * <ul>
 *   <li>unwraps the {@code ApiResponse} envelope and throws a typed {@link ApiError};</li>
 *   <li>attaches the bearer token;</li>
 *   <li>refreshes the token once on a 401 and replays the original request;</li>
 *   <li>de-duplicates concurrent refreshes so a burst of 401s triggers exactly one.</li>
 * </ul>
 */

/** Shared promise so ten simultaneous 401s produce one refresh, not ten. */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) return false;

  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        const payload = (await response.json()) as ApiResponse<{
          accessToken: string;
          refreshToken: string;
          userId: string;
        }>;

        if (!response.ok || !payload.success || !payload.data) {
          tokenStore.clear();
          return false;
        }
        tokenStore.setSession(payload.data);
        return true;
      } catch {
        tokenStore.clear();
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(path.startsWith('http') ? path : `${BASE_URL}${path}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }
  return url.toString();
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, formData, query, signal, anonymous, isRetry } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (!formData && body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (!anonymous) {
    const token = tokenStore.getAccessToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      signal,
      body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw ApiError.network();
  }

  // 401 once means the access token aged out; refresh and replay exactly one time.
  if (response.status === 401 && !anonymous && !isRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return request<T>(path, { ...options, isRetry: true });
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }

  let payload: ApiResponse<T>;
  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError(
      { code: 'INTERNAL_ERROR', message: 'The server returned an unreadable response' },
      response.status,
    );
  }

  if (!response.ok || !payload.success) {
    throw new ApiError(
      payload.error ?? { code: 'INTERNAL_ERROR', message: 'Something went wrong' },
      response.status,
      payload.traceId,
    );
  }

  return payload.data as T;
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE', body }),
  upload: <T>(path: string, formData: FormData, options?: Omit<RequestOptions, 'method' | 'formData'>) =>
    request<T>(path, { ...options, method: 'POST', formData }),
};

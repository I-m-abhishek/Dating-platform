import type { ApiErrorBody, ErrorCode, FieldViolation } from './types';

/**
 * Every failure the app can see, in one shape.
 *
 * Components branch on {@link ApiError.code}, never on the message text - messages are
 * copy and change; codes are the contract.
 */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly details: FieldViolation[];
  readonly context: unknown;
  readonly traceId?: string;

  constructor(body: ApiErrorBody, status: number, traceId?: string) {
    super(body.message);
    this.name = 'ApiError';
    this.code = body.code;
    this.status = status;
    this.details = body.details ?? [];
    this.context = body.context;
    this.traceId = traceId;
  }

  static network(message = 'We could not reach the server. Check your connection.'): ApiError {
    return new ApiError({ code: 'NETWORK_ERROR', message }, 0);
  }

  /** Maps field violations to the shape a form needs: { fieldName: message }. */
  get fieldErrors(): Record<string, string> {
    return this.details.reduce<Record<string, string>>((acc, violation) => {
      acc[violation.field] = violation.message;
      return acc;
    }, {});
  }

  get isAuthError(): boolean {
    return this.code === 'UNAUTHORIZED' || this.code === 'TOKEN_EXPIRED' || this.code === 'TOKEN_INVALID';
  }

  /** True when the right response is a paywall, not an error toast. */
  get isPaywall(): boolean {
    return this.code === 'QUOTA_EXCEEDED' || this.code === 'PREMIUM_REQUIRED';
  }

  get isRetryable(): boolean {
    return this.code === 'NETWORK_ERROR' || this.code === 'INTERNAL_ERROR' || this.status >= 500;
  }
}

/** Quota context, present on QUOTA_EXCEEDED. */
export interface QuotaContext {
  feature: string;
  limit: number;
  resetsAt: string;
  upgradeHint?: string;
}

/** Premium context, present on PREMIUM_REQUIRED. */
export interface PremiumContext {
  feature: string;
  requiredTier: 'PLUS' | 'PREMIUM';
}

export function quotaContextOf(error: unknown): QuotaContext | null {
  if (error instanceof ApiError && error.code === 'QUOTA_EXCEEDED' && error.context) {
    return error.context as QuotaContext;
  }
  return null;
}

export function premiumContextOf(error: unknown): PremiumContext | null {
  if (error instanceof ApiError && error.code === 'PREMIUM_REQUIRED' && error.context) {
    return error.context as PremiumContext;
  }
  return null;
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong';
}

'use client';

import { Component as ReactComponent, type ComponentType, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from '@/components/ui/ErrorState';

interface BoundaryProps {
  children: ReactNode;
  fallback?: (error: Error, reset: () => void) => ReactNode;
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface BoundaryState {
  error: Error | null;
}

/**
 * React still has no hook for catching render errors, so this stays a class.
 * It is the only class component in the codebase, and it is here for that reason.
 */
export class ErrorBoundary extends ReactComponent<BoundaryProps, BoundaryState> {
  override state: BoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): BoundaryState {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Wire this to your error reporter. Console is deliberate: a swallowed render error
    // is far more expensive to debug than a noisy one.
    console.error('Render error caught by boundary', error, info.componentStack);
    this.props.onError?.(error, info);
  }

  private readonly reset = (): void => {
    this.setState({ error: null });
  };

  override render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }
    if (this.props.fallback) {
      return this.props.fallback(error, this.reset);
    }
    return (
      <ErrorState
        title="This section stopped working"
        description="Reloading usually fixes it. If it keeps happening, let us know."
        onRetry={this.reset}
      />
    );
  }
}

/**
 * Wraps a component so a render error takes out one screen instead of the whole app.
 *
 * <p>Applied at the page level in this codebase: a crash in the chat thread should not
 * blank the navigation the user needs to get out of it.
 *
 * @example
 * export default withErrorBoundary(withAuth(ChatPage));
 */
export function withErrorBoundary<P extends object>(
  Component: ComponentType<P>,
  fallback?: BoundaryProps['fallback'],
): ComponentType<P> {
  function Boundaried(props: P) {
    return (
      <ErrorBoundary fallback={fallback}>
        <Component {...props} />
      </ErrorBoundary>
    );
  }

  Boundaried.displayName = `withErrorBoundary(${Component.displayName ?? Component.name ?? 'Component'})`;
  return Boundaried;
}

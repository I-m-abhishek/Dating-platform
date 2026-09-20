import type { ReactNode } from 'react';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-bg">
      <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-6 py-12">
        <div className="mb-10 text-center">
          <p className="text-2xl font-semibold tracking-tight text-ink">
            Two <span className="text-accent">&amp;</span> Two
          </p>
          <p className="mt-1.5 text-sm text-ink-muted">
            One good match at a time.
          </p>
        </div>
        {children}
      </div>
    </div>
  );
}

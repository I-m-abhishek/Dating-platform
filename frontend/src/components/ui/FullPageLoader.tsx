import { Spinner } from './Spinner';

export function FullPageLoader({ label }: { label?: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-ink-subtle">
      <Spinner className="h-6 w-6 text-accent" />
      {label ? <p className="text-sm">{label}</p> : null}
    </div>
  );
}

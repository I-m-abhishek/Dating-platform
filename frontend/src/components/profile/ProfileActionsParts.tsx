'use client';

import { Button } from '@/components/ui/Button';

/** One row in the actions menu. Destructive rows carry the danger tint on the icon and label. */
export function ActionRow({
  icon,
  label,
  description,
  tone,
  onClick,
}: {
  icon: string;
  label: string;
  description: string;
  tone?: 'danger';
  onClick: () => void;
}) {
  const danger = tone === 'danger';
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition-colors hover:bg-surface-muted"
    >
      <span
        aria-hidden
        className={
          danger
            ? 'flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-danger/10 text-danger'
            : 'flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-surface-muted text-ink-muted'
        }
      >
        {icon}
      </span>
      <span className="min-w-0">
        <span className={danger ? 'block text-sm font-medium text-danger' : 'block text-sm font-medium text-ink'}>
          {label}
        </span>
        <span className="block text-xs text-ink-subtle">{description}</span>
      </span>
    </button>
  );
}

/** Second step for a destructive action: says who, and what will happen. */
export function ConfirmPanel({
  title,
  body,
  confirmLabel,
  busy,
  onCancel,
  onConfirm,
}: {
  title: string;
  body: string;
  confirmLabel: string;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        <p className="mt-1.5 text-sm text-ink-muted">{body}</p>
      </div>
      <div className="flex gap-3">
        <Button variant="ghost" fullWidth onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="danger" fullWidth loading={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  );
}

'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Textarea';
import { matchApi, safetyApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useUiStore } from '@/lib/stores/uiStore';
import { messageOf } from '@/lib/api/errors';
import { CloseIcon, LockIcon, ShieldCheckIcon } from '@/components/ui/icons';
import { ActionRow, ConfirmPanel } from './ProfileActionsParts';

const REPORT_REASONS = [
  { value: 'FAKE_PROFILE', label: 'Fake profile' },
  { value: 'HARASSMENT', label: 'Harassment or abuse' },
  { value: 'INAPPROPRIATE_CONTENT', label: 'Inappropriate content' },
  { value: 'SPAM_OR_SCAM', label: 'Spam or a scam' },
  { value: 'UNDERAGE', label: 'They appear to be under 18' },
  { value: 'OFF_PLATFORM_BEHAVIOUR', label: 'Something that happened elsewhere' },
  { value: 'OTHER', label: 'Something else' },
] as const;

type View = 'menu' | 'confirmUnmatch' | 'confirmBlock' | 'report';

export interface ProfileActionsSheetProps {
  open: boolean;
  onClose: () => void;
  userId: string;
  displayName: string;
  /** Present when the two of you are matched - enables Unmatch. */
  matchId?: string;
}

/**
 * Unmatch, block and report.
 *
 * <p>All three are hard to undo, so none of them fire from a single tap: each steps through
 * an explicit confirmation that names the person and says what will happen. The destructive
 * options are visually separated from Report, which is not destructive to the reporter.
 */
export function ProfileActionsSheet({
  open,
  onClose,
  userId,
  displayName,
  matchId,
}: ProfileActionsSheetProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);

  const [view, setView] = useState<View>('menu');
  const [reason, setReason] = useState<string | null>(null);
  const [details, setDetails] = useState('');
  const [busy, setBusy] = useState(false);

  const close = () => {
    setView('menu');
    setReason(null);
    setDetails('');
    onClose();
  };

  const run = async (action: () => Promise<unknown>, success: string, goTo?: string) => {
    setBusy(true);
    try {
      await action();
      toast({ title: success, tone: 'success' });
      // The person vanishes from matches, chat and discovery at once, so all three go.
      void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.discovery.all });
      close();
      if (goTo) router.replace(goTo);
    } catch (error) {
      toast({ title: messageOf(error), tone: 'error' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet open={open} onClose={close} size="tall">
      {view === 'menu' ? (
        <div className="space-y-1">
          <h2 className="px-1 pb-3 font-display text-[21px] font-semibold tracking-[-0.02em] text-ink">
            {displayName}
          </h2>

          {matchId ? (
            <ActionRow
              icon={<CloseIcon size={18} />}
              label="Unmatch"
              description="Removes the match and closes your chat"
              tone="danger"
              onClick={() => setView('confirmUnmatch')}
            />
          ) : null}

          <ActionRow
            icon={<LockIcon size={18} />}
            label="Block"
            description="You will not see each other anywhere"
            tone="danger"
            onClick={() => setView('confirmBlock')}
          />

          <ActionRow
            icon={<ShieldCheckIcon size={18} />}
            label="Report"
            description="Send this to our safety team"
            onClick={() => setView('report')}
          />

          <Button variant="ghost" size="lg" fullWidth onClick={close} className="mt-3">
            Cancel
          </Button>
        </div>
      ) : null}

      {view === 'confirmUnmatch' ? (
        <ConfirmPanel
          title={`Unmatch ${displayName}?`}
          body="Your conversation will close and neither of you will appear in the other's matches. This cannot be undone."
          confirmLabel="Unmatch"
          busy={busy}
          onCancel={() => setView('menu')}
          onConfirm={() =>
            void run(() => matchApi.unmatch(matchId as string), 'Unmatched', '/matches')
          }
        />
      ) : null}

      {view === 'confirmBlock' ? (
        <ConfirmPanel
          title={`Block ${displayName}?`}
          body="You will disappear from each other's discovery, likes and messages. Any match between you is removed."
          confirmLabel="Block"
          busy={busy}
          onCancel={() => setView('menu')}
          onConfirm={() => void run(() => safetyApi.block(userId), 'Blocked', '/home')}
        />
      ) : null}

      {view === 'report' ? (
        <div className="space-y-4">
          <div>
            <h2 className="font-display text-[21px] font-semibold tracking-[-0.02em] text-ink">
              Report {displayName}
            </h2>
            <p className="mt-2 text-sm leading-relaxed text-ink-muted">
              Reports are confidential - they are never shown to the person reported.
            </p>
          </div>

          <div className="space-y-1.5">
            {REPORT_REASONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setReason(option.value)}
                aria-pressed={reason === option.value}
                className={
                  reason === option.value
                    ? 'w-full rounded-xl2 border border-accent bg-accent-soft px-4 py-3.5 text-left text-sm font-semibold text-accent'
                    : 'w-full rounded-xl2 border border-border px-4 py-3.5 text-left text-sm text-ink-muted transition-colors hover:border-border-strong hover:bg-surface-muted'
                }
              >
                {option.label}
              </button>
            ))}
          </div>

          <Textarea
            label="Anything else we should know? (optional)"
            value={details}
            counterMax={1000}
            rows={3}
            onChange={(event) => setDetails(event.target.value)}
          />

          <div className="flex gap-3">
            <Button variant="ghost" size="lg" fullWidth onClick={() => setView('menu')}>
              Back
            </Button>
            <Button
              variant="danger"
              size="lg"
              fullWidth
              loading={busy}
              disabled={!reason}
              onClick={() =>
                void run(
                  () =>
                    safetyApi.report({
                      reportedUserId: userId,
                      reason: reason as string,
                      details: details.trim() || undefined,
                      alsoBlock: true,
                    }),
                  'Thanks - our team will take a look',
                  '/home',
                )
              }
            >
              Report and block
            </Button>
          </div>
        </div>
      ) : null}
    </Sheet>
  );
}

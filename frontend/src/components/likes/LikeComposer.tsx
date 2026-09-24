'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Textarea';
import { HeartIcon, StarIcon } from '@/components/ui/icons';
import { useLikeQuota } from '@/lib/hooks/useLikes';
import { cn } from '@/lib/utils/cn';

const MAX_COMMENT = 200;

export interface LikeComposerProps {
  /** Who is being liked, for the placeholder and labels. */
  name: string;
  /** What the comment is about - "photo" or "prompt". */
  about: 'photo' | 'prompt';
  /** Resolves true once sent; the box then clears. */
  onSend: (input: { note?: string; superLike: boolean }) => Promise<boolean>;
  autoFocus?: boolean;
  className?: string;
}

/**
 * The comment box that sits under a photo or prompt.
 *
 * <p>Two ways to send. "Send like" is the everyday like, with the comment attached.
 * "Super send" is the priority lane: it goes to the top of their Likes, puts you at the
 * front of their deck with a badge, and comes from a small daily allowance - which is
 * why it means something. Either way the comment opens the chat if they like you back.
 *
 * <p>The comment is optional; a like on a specific photo or prompt already says more than
 * a swipe. But the placeholder nudges towards writing one, because that is what gets
 * answered.
 */
export function LikeComposer({ name, about, onSend, autoFocus, className }: LikeComposerProps) {
  const [note, setNote] = useState('');
  const [sending, setSending] = useState<'like' | 'super' | null>(null);
  const quota = useLikeQuota();

  const superLeft = quota.data?.superLikesRemaining;
  const superLabel =
    superLeft == null ? null : superLeft < 0 ? 'Unlimited' : `${superLeft} left today`;

  const send = async (superLike: boolean) => {
    setSending(superLike ? 'super' : 'like');
    try {
      const trimmed = note.trim();
      const sent = await onSend({ note: trimmed || undefined, superLike });
      if (sent) setNote('');
    } finally {
      setSending(null);
    }
  };

  return (
    <div className={cn('space-y-3 rounded-xl2 border border-border bg-surface p-4 shadow-card', className)}>
      <Textarea
        value={note}
        onChange={(event) => setNote(event.target.value.slice(0, MAX_COMMENT))}
        maxLength={MAX_COMMENT}
        counterMax={MAX_COMMENT}
        rows={3}
        autoFocus={autoFocus}
        placeholder={
          about === 'photo'
            ? `Say something about this photo - ${name} is more likely to reply to a comment.`
            : `Answer ${name}'s prompt - a thoughtful reply stands out.`
        }
        aria-label={`Comment on ${name}'s ${about}`}
      />

      <div className="flex gap-2.5">
        <Button
          variant="outline"
          fullWidth
          loading={sending === 'like'}
          disabled={sending !== null}
          onClick={() => void send(false)}
          leftIcon={<HeartIcon size={17} filled />}
        >
          Send like
        </Button>
        {/* Secondary as the base: it carries no gradient of its own to fight this one. */}
        <Button
          variant="secondary"
          fullWidth
          loading={sending === 'super'}
          disabled={sending !== null}
          onClick={() => void send(true)}
          leftIcon={<StarIcon size={17} filled />}
          className="bg-gradient-to-r from-sky-500 to-indigo-500 text-white shadow-[0_8px_22px_-10px_rgb(56_130_246/0.9)] hover:brightness-105"
        >
          Super send
        </Button>
      </div>

      <p className="text-[11.5px] leading-snug text-ink-subtle">
        <span className="font-semibold text-sky-600 dark:text-sky-400">Super send</span> puts you at the top of
        their likes and the front of their deck.
        {superLabel ? <span className="font-semibold text-ink-muted"> {superLabel}.</span> : null}
      </p>
    </div>
  );
}

'use client';

import type { ReactNode } from 'react';
import { LikeComposer } from '@/components/likes/LikeComposer';
import { CommentIcon, HeartIcon } from '@/components/ui/icons';
import { cn } from '@/lib/utils/cn';
import type { LikeTarget } from '@/components/likes/LikeTargetSheet';
import type { LikeIntent } from '@/lib/api/types';

export interface LikeableItemProps {
  name: string;
  target: LikeTarget;
  /** Only one box is open at a time; the page owns which. */
  open: boolean;
  onToggle: () => void;
  onSend: (intent: LikeIntent) => Promise<boolean>;
  /** False once liked or matched - there is nothing left to send. */
  enabled: boolean;
  children: ReactNode;
}

/**
 * A photo or prompt on a full profile, with its own "like & comment" button and, when
 * opened, the comment box directly beneath it.
 */
export function LikeableItem({ name, target, open, onToggle, onSend, enabled, children }: LikeableItemProps) {
  const about = target.kind;
  return (
    <div className="space-y-2">
      {children}
      {enabled ? (
        <>
          <div className="flex justify-end">
            <button
              type="button"
              onClick={onToggle}
              aria-expanded={open}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-pill px-3.5 py-2 text-xs font-semibold transition-all duration-200 ease-snap active:scale-95',
                open
                  ? 'bg-ink text-surface'
                  : 'bg-accent-soft text-accent hover:bg-accent hover:text-white',
              )}
            >
              {open ? <CommentIcon size={14} /> : <HeartIcon size={14} filled />}
              {open ? 'Close' : about === 'photo' ? 'Like & comment' : 'Reply to prompt'}
            </button>
          </div>
          {open ? (
            <LikeComposer
              name={name}
              about={about}
              autoFocus
              className="animate-scale-in"
              onSend={({ note, superLike }) =>
                onSend({
                  note,
                  superLike,
                  targetPhotoId: target.kind === 'photo' ? target.photo.id : undefined,
                  targetPromptAnswerId: target.kind === 'prompt' ? target.prompt.id : undefined,
                })
              }
            />
          ) : null}
        </>
      ) : null}
    </div>
  );
}

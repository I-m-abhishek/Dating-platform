'use client';

import Image from 'next/image';
import { Sheet } from '@/components/ui/Sheet';
import { LikeComposer } from '@/components/likes/LikeComposer';
import type { LikeIntent, Photo, PromptAnswer } from '@/lib/api/types';

export type LikeTarget = { kind: 'photo'; photo: Photo } | { kind: 'prompt'; prompt: PromptAnswer };

export interface LikeTargetSheetProps {
  name: string;
  target: LikeTarget | null;
  onClose: () => void;
  /** Resolves true when the like went through. */
  onSend: (intent: LikeIntent) => Promise<boolean>;
}

/**
 * Liking one specific photo or prompt: the thing itself on top, the comment box right
 * below it, so the comment is written while looking at what it is about.
 */
export function LikeTargetSheet({ name, target, onClose, onSend }: LikeTargetSheetProps) {
  return (
    <Sheet
      open={Boolean(target)}
      onClose={onClose}
      title={target?.kind === 'prompt' ? `Reply to ${name}` : `Comment on ${name}'s photo`}
      size="tall"
    >
      {target ? (
        <div className="space-y-4">
          {target.kind === 'photo' ? (
            <div className="relative mx-auto aspect-[4/5] max-h-[46dvh] w-full overflow-hidden rounded-xl2 bg-surface-muted shadow-card">
              <Image
                src={target.photo.url}
                alt={`${name}'s photo`}
                fill
                sizes="(max-width: 768px) 100vw, 480px"
                className="object-cover"
                unoptimized
              />
            </div>
          ) : (
            <section className="space-y-1.5 rounded-xl2 bg-surface-muted p-5">
              <p className="eyebrow">{target.prompt.prompt}</p>
              <p className="font-display text-[20px] leading-[1.35] text-ink">{target.prompt.answer}</p>
            </section>
          )}

          <LikeComposer
            name={name}
            about={target.kind}
            autoFocus
            onSend={({ note, superLike }) =>
              onSend({
                note,
                superLike,
                targetPhotoId: target.kind === 'photo' ? target.photo.id : undefined,
                targetPromptAnswerId: target.kind === 'prompt' ? target.prompt.id : undefined,
              })
            }
          />
        </div>
      ) : null}
    </Sheet>
  );
}

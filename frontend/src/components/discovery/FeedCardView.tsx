'use client';

import { PhotoCarousel } from '@/components/profile/PhotoCarousel';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import type { FeedCard, Photo } from '@/lib/api/types';

export interface FeedCardViewProps {
  card: FeedCard;
  onLike: (photo?: Photo) => void;
  onPass: () => void;
  onCommentPhoto: (photo: Photo) => void;
  busy?: boolean;
}

/**
 * One profile in the feed.
 *
 * <p>Prompts are interleaved between photos rather than stacked below them. That is the
 * whole point of a prompt-led profile: the reader meets a sentence, then a face, then
 * another sentence, instead of scrolling past a gallery to reach the words.
 */
export function FeedCardView({ card, onLike, onPass, onCommentPhoto, busy }: FeedCardViewProps) {
  const [firstPhoto, ...restPhotos] = card.photos;
  const [firstPrompt, ...restPrompts] = card.prompts;
  const distance = distanceLabel(card.distanceKm);
  const compatibility = compatibilityLabel(card.compatibilityScore);

  return (
    <article className="card overflow-hidden">
      <header className="flex items-start justify-between gap-3 p-4">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-semibold text-ink">
            {card.displayName}, {card.age}
          </h2>
          <p className="mt-0.5 truncate text-sm text-ink-muted">
            {[card.jobTitle, distance].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1">
          {compatibility ? <Badge tone="accent">{compatibility}</Badge> : null}
          {card.recentlyActive ? <Badge tone="success">Active recently</Badge> : null}
        </div>
      </header>

      {firstPhoto ? (
        <PhotoCarousel
          photos={[firstPhoto]}
          alt={card.displayName}
          onCommentPhoto={onCommentPhoto}
          onLikePhoto={(photo) => onLike(photo)}
          className="rounded-none"
        />
      ) : null}

      {card.highlights.length > 0 ? (
        <div className="flex flex-wrap gap-2 px-4 pt-4">
          {card.highlights.map((highlight) => (
            <Badge key={highlight} tone="muted">
              {highlight}
            </Badge>
          ))}
        </div>
      ) : null}

      {firstPrompt ? (
        <section className="space-y-1.5 p-4">
          <p className="text-xs uppercase tracking-wide text-ink-subtle">{firstPrompt.prompt}</p>
          <p className="text-[17px] leading-snug text-ink">{firstPrompt.answer}</p>
        </section>
      ) : null}

      {card.bio ? (
        <p className="px-4 pb-4 text-[15px] leading-relaxed text-ink-muted">{card.bio}</p>
      ) : null}

      {restPhotos.length > 0 ? (
        <PhotoCarousel
          photos={restPhotos}
          alt={card.displayName}
          onCommentPhoto={onCommentPhoto}
          onLikePhoto={(photo) => onLike(photo)}
          className="rounded-none"
        />
      ) : null}

      {restPrompts.map((prompt) => (
        <section key={prompt.id} className="space-y-1.5 border-t border-border p-4">
          <p className="text-xs uppercase tracking-wide text-ink-subtle">{prompt.prompt}</p>
          <p className="text-[17px] leading-snug text-ink">{prompt.answer}</p>
        </section>
      ))}

      <div className="border-t border-border p-4">
        <ProfileDetails
          jobTitle={card.jobTitle}
          school={card.school}
          heightCm={card.heightCm}
          interests={card.interests}
          qualities={card.qualities}
          sharedInterests={card.sharedInterests}
        />
      </div>

      <footer className="sticky bottom-0 flex gap-3 border-t border-border bg-surface/95 p-4 backdrop-blur-md">
        <Button variant="outline" size="lg" fullWidth onClick={onPass} disabled={busy}>
          Pass
        </Button>
        <Button size="lg" fullWidth onClick={() => onLike()} disabled={busy}>
          Like
        </Button>
      </footer>
    </article>
  );
}

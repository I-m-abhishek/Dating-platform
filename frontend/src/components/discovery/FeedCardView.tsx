'use client';

import { PhotoCarousel } from '@/components/profile/PhotoCarousel';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { CloseIcon, HeartIcon, MapPinIcon } from '@/components/ui/icons';
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
 *
 * <p>The name sits on the first photo rather than in a header above it. A card that opens
 * with a face and a name is a person; one that opens with a text row and then a picture is
 * a database record.
 */
export function FeedCardView({ card, onLike, onPass, onCommentPhoto, busy }: FeedCardViewProps) {
  const [firstPhoto, ...restPhotos] = card.photos;
  const [firstPrompt, ...restPrompts] = card.prompts;
  const distance = distanceLabel(card.distanceKm);
  const compatibility = compatibilityLabel(card.compatibilityScore);

  const nameBlock = (
    <>
      <h2 className="font-display text-[30px] font-semibold leading-none tracking-[-0.02em] text-white drop-shadow-sm">
        {card.displayName}
        <span className="font-sans text-[22px] font-medium text-white/75"> {card.age}</span>
      </h2>
      <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] font-medium text-white/80">
        {card.jobTitle ? <span>{card.jobTitle}</span> : null}
        {card.jobTitle && distance ? <span className="text-white/40">·</span> : null}
        {distance ? (
          <span className="inline-flex items-center gap-1">
            <MapPinIcon size={14} />
            {distance}
          </span>
        ) : null}
      </p>
    </>
  );

  return (
    <article className="card overflow-hidden">
      {firstPhoto ? (
        <div className="relative">
          <PhotoCarousel
            photos={[firstPhoto]}
            alt={card.displayName}
            aspect="tall"
            onCommentPhoto={onCommentPhoto}
            onLikePhoto={(photo) => onLike(photo)}
            overlay={nameBlock}
          />

          {/* Left column, so it never collides with the photo action buttons on the right. */}
          <div className="pointer-events-none absolute left-4 top-4 flex flex-col items-start gap-1.5">
            {compatibility ? <Badge tone="accent">{compatibility}</Badge> : null}
            {card.recentlyActive ? (
              <Badge tone="glass">
                <span className="mr-0.5 h-1.5 w-1.5 rounded-full bg-success" />
                Active now
              </Badge>
            ) : null}
          </div>
        </div>
      ) : (
        <header className="flex items-start justify-between gap-3 p-5">
          <div className="min-w-0">
            <h2 className="truncate font-display text-2xl font-semibold text-ink">
              {card.displayName}, {card.age}
            </h2>
            <p className="mt-0.5 truncate text-sm text-ink-muted">
              {[card.jobTitle, distance].filter(Boolean).join(' · ')}
            </p>
          </div>
          {compatibility ? <Badge tone="accent">{compatibility}</Badge> : null}
        </header>
      )}

      {card.highlights.length > 0 ? (
        <div className="flex flex-wrap gap-2 px-5 pt-5">
          {card.highlights.map((highlight) => (
            <Badge key={highlight} tone="muted">
              {highlight}
            </Badge>
          ))}
        </div>
      ) : null}

      {firstPrompt ? <Prompt prompt={firstPrompt.prompt} answer={firstPrompt.answer} /> : null}

      {card.bio ? (
        <p className="px-5 pb-5 text-[15px] leading-relaxed text-ink-muted">{card.bio}</p>
      ) : null}

      {restPhotos.length > 0 ? (
        <PhotoCarousel
          photos={restPhotos}
          alt={card.displayName}
          onCommentPhoto={onCommentPhoto}
          onLikePhoto={(photo) => onLike(photo)}
        />
      ) : null}

      {restPrompts.map((prompt) => (
        <Prompt key={prompt.id} prompt={prompt.prompt} answer={prompt.answer} bordered />
      ))}

      <div className="border-t border-border p-5">
        <ProfileDetails
          jobTitle={card.jobTitle}
          school={card.school}
          heightCm={card.heightCm}
          interests={card.interests}
          qualities={card.qualities}
          sharedInterests={card.sharedInterests}
        />
      </div>

      {/*
        Not sticky, despite looking like it should be. The card clips its children so the
        photo follows the rounded corners, and `overflow: hidden` makes the card its own
        scrollport - a sticky child inside it has nothing to stick to and simply sits still.
        Rather than leave dead position code here, the bar ends the card honestly.
      */}
      <footer className="flex gap-3 border-t border-border bg-surface p-4">
        <Button
          variant="outline"
          size="lg"
          fullWidth
          onClick={onPass}
          disabled={busy}
          leftIcon={<CloseIcon size={18} />}
        >
          Pass
        </Button>
        <Button
          size="lg"
          fullWidth
          onClick={() => onLike()}
          disabled={busy}
          leftIcon={<HeartIcon size={18} />}
        >
          Like
        </Button>
      </footer>
    </article>
  );
}

/**
 * A prompt and its answer.
 *
 * The answer is set in the display serif at reading size - it is the one piece of a profile
 * that someone actually wrote, so it gets treated as writing rather than as metadata.
 */
function Prompt({
  prompt,
  answer,
  bordered,
}: {
  prompt: string;
  answer: string;
  bordered?: boolean;
}) {
  return (
    <section className={`space-y-2 p-5 ${bordered ? 'border-t border-border' : ''}`}>
      <p className="eyebrow">{prompt}</p>
      <p className="font-display text-[20px] leading-[1.35] text-ink">{answer}</p>
    </section>
  );
}

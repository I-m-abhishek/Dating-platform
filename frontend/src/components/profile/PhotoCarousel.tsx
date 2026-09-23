'use client';

import Image from 'next/image';
import { useState, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';
import { ChevronRightIcon, ImageIcon } from '@/components/ui/icons';
import type { Photo } from '@/lib/api/types';

export interface PhotoCarouselProps {
  photos: Photo[];
  alt: string;
  /** Rendered over the scrim at the bottom. */
  overlay?: ReactNode;
  aspect?: 'portrait' | 'tall';
  className?: string;
}

/**
 * Tap the left or right half to step through photos - the gesture people already know from
 * every other dating app. Keyboard arrows do the same thing, which is the part those apps
 * usually forget.
 *
 * <p>Everything laid over the image sits on either a scrim or a blurred puck, because the
 * one thing you cannot predict about a user-uploaded photo is how light the corner is.
 */
export function PhotoCarousel({
  photos,
  alt,
  overlay,
  aspect = 'portrait',
  className,
}: PhotoCarouselProps) {
  const [index, setIndex] = useState(0);
  const photo = photos[index];
  const ratio = aspect === 'tall' ? 'aspect-[4/5]' : 'aspect-[3/4]';

  if (!photo) {
    return (
      <div
        className={cn(
          'flex flex-col items-center justify-center gap-2 rounded-card bg-surface-muted text-ink-subtle',
          ratio,
          className,
        )}
      >
        <ImageIcon size={26} />
        <span className="text-sm font-medium">No photos yet</span>
      </div>
    );
  }

  const step = (delta: number) => {
    setIndex((current) => Math.min(Math.max(current + delta, 0), photos.length - 1));
  };

  const hasScrim = Boolean(overlay || photo.caption);

  return (
    <div
      className={cn('group/photo relative overflow-hidden bg-surface-muted', ratio, className)}
      onKeyDown={(event) => {
        if (event.key === 'ArrowRight') step(1);
        if (event.key === 'ArrowLeft') step(-1);
      }}
      tabIndex={0}
      role="group"
      aria-label={`${alt}, photo ${index + 1} of ${photos.length}`}
    >
      <Image
        src={photo.url}
        alt={alt}
        fill
        sizes="(max-width: 768px) 100vw, 640px"
        className="object-cover"
        priority={index === 0}
        unoptimized
      />

      {hasScrim ? <div aria-hidden className="absolute inset-0 bg-photo-scrim" /> : null}

      {photos.length > 1 ? (
        <>
          <div className="pointer-events-none absolute inset-x-3 top-3 flex gap-1.5">
            {photos.map((item, itemIndex) => (
              <span
                key={item.id}
                className={cn(
                  'h-[3px] flex-1 rounded-full backdrop-blur-sm transition-colors duration-300',
                  itemIndex === index ? 'bg-white' : 'bg-white/30',
                )}
              />
            ))}
          </div>

          <button
            type="button"
            aria-label="Previous photo"
            className="absolute inset-y-0 left-0 w-1/3 focus:outline-none"
            onClick={() => step(-1)}
          />
          <button
            type="button"
            aria-label="Next photo"
            className="absolute inset-y-0 right-0 w-1/3 focus:outline-none"
            onClick={() => step(1)}
          />

          {/*
            Visible affordances for a pointer, which has no way to discover that the left and
            right thirds of an image are tappable. Hidden from assistive tech because the
            full-height buttons above already carry those labels.
          */}
          {index > 0 ? (
            <span
              aria-hidden
              className="glass-dark pointer-events-none absolute left-3 top-1/2 hidden h-9 w-9 -translate-y-1/2 rotate-180 items-center justify-center rounded-full text-white opacity-0 transition-opacity duration-200 group-hover/photo:opacity-100 md:flex"
            >
              <ChevronRightIcon size={17} />
            </span>
          ) : null}
          {index < photos.length - 1 ? (
            <span
              aria-hidden
              className="glass-dark pointer-events-none absolute right-3 top-1/2 hidden h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full text-white opacity-0 transition-opacity duration-200 group-hover/photo:opacity-100 md:flex"
            >
              <ChevronRightIcon size={17} />
            </span>
          ) : null}
        </>
      ) : null}

      {photo.caption ? (
        <p className="pointer-events-none absolute inset-x-0 bottom-0 px-5 pb-5 text-sm font-medium text-white/90">
          {photo.caption}
        </p>
      ) : null}

      {overlay ? <div className="absolute inset-x-0 bottom-0 p-5">{overlay}</div> : null}
    </div>
  );
}

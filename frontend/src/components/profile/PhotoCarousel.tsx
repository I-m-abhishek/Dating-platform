'use client';

import Image from 'next/image';
import { useState } from 'react';
import { cn } from '@/lib/utils/cn';
import type { Photo } from '@/lib/api/types';

export interface PhotoCarouselProps {
  photos: Photo[];
  alt: string;
  onCommentPhoto?: (photo: Photo) => void;
  onLikePhoto?: (photo: Photo) => void;
  className?: string;
}

/**
 * Tap the left or right half to step through photos - the gesture people already know from
 * every other dating app. Keyboard arrows do the same thing, which is the part those apps
 * usually forget.
 */
export function PhotoCarousel({
  photos,
  alt,
  onCommentPhoto,
  onLikePhoto,
  className,
}: PhotoCarouselProps) {
  const [index, setIndex] = useState(0);
  const photo = photos[index];

  if (!photo) {
    return (
      <div className={cn('flex aspect-[3/4] items-center justify-center rounded-card bg-surface-muted', className)}>
        <span className="text-sm text-ink-subtle">No photos yet</span>
      </div>
    );
  }

  const step = (delta: number) => {
    setIndex((current) => Math.min(Math.max(current + delta, 0), photos.length - 1));
  };

  return (
    <div
      className={cn('relative aspect-[3/4] overflow-hidden rounded-card bg-surface-muted', className)}
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

      {photos.length > 1 ? (
        <>
          <div className="pointer-events-none absolute inset-x-3 top-3 flex gap-1">
            {photos.map((item, itemIndex) => (
              <span
                key={item.id}
                className={cn(
                  'h-1 flex-1 rounded-full transition-colors',
                  itemIndex === index ? 'bg-white' : 'bg-white/35',
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
        </>
      ) : null}

      {photo.caption ? (
        <p className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-4 pb-4 pt-10 text-sm text-white">
          {photo.caption}
        </p>
      ) : null}

      {(onCommentPhoto || onLikePhoto) ? (
        <div className="absolute bottom-3 right-3 flex gap-2">
          {onCommentPhoto ? (
            <button
              type="button"
              onClick={() => onCommentPhoto(photo)}
              className="flex items-center gap-1 rounded-pill bg-black/45 px-3 py-1.5 text-xs text-white backdrop-blur-sm"
            >
              💬 {photo.commentCount > 0 ? photo.commentCount : 'Comment'}
            </button>
          ) : null}
          {onLikePhoto ? (
            <button
              type="button"
              onClick={() => onLikePhoto(photo)}
              aria-label="Like this photo"
              className="rounded-pill bg-black/45 px-3 py-1.5 text-xs text-white backdrop-blur-sm"
            >
              ♡
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

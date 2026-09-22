'use client';

import Image from 'next/image';
import { cn } from '@/lib/utils/cn';
import { initials } from '@/lib/utils/format';

export interface AvatarProps {
  src?: string | null;
  name?: string | null;
  size?: number;
  online?: boolean;
  /** 'gradient' marks something new or unread; 'accent' marks the current selection. */
  ring?: 'none' | 'accent' | 'gradient';
  className?: string;
}

/**
 * Falls back to initials, because a broken image is worse than no image.
 *
 * <p>The fallback tint is derived from the name rather than fixed, so a list of people
 * without photos still reads as a list of individuals instead of a column of identical grey
 * circles.
 */
function hueFor(name?: string | null) {
  if (!name) return 330;
  let hash = 0;
  for (let index = 0; index < name.length; index += 1) {
    hash = (hash * 31 + name.charCodeAt(index)) % 360;
  }
  return hash;
}

export function Avatar({ src, name, size = 48, online, ring = 'none', className }: AvatarProps) {
  const hue = hueFor(name);

  const inner = (
    <span className="relative block h-full w-full">
      {src ? (
        <Image
          src={src}
          alt={name ?? 'Profile photo'}
          width={size}
          height={size}
          className="h-full w-full rounded-full object-cover"
          unoptimized
        />
      ) : (
        <span
          aria-hidden
          className="flex h-full w-full items-center justify-center rounded-full font-semibold text-white"
          style={{
            fontSize: size * 0.36,
            backgroundImage: `linear-gradient(140deg, hsl(${hue} 72% 62%), hsl(${(hue + 48) % 360} 68% 52%))`,
          }}
        >
          {initials(name)}
        </span>
      )}

      {online ? (
        <span
          className="absolute bottom-0 right-0 block rounded-full border-[2.5px] border-surface bg-success"
          style={{ width: size * 0.27, height: size * 0.27 }}
          aria-label="Active recently"
        />
      ) : null}
    </span>
  );

  if (ring === 'none') {
    return (
      <span
        className={cn('relative inline-block shrink-0', className)}
        style={{ width: size, height: size }}
      >
        {inner}
      </span>
    );
  }

  // The ring is a padded gradient backdrop rather than a border, so it can be two colours.
  return (
    <span
      className={cn(
        'relative inline-block shrink-0 rounded-full p-[2.5px]',
        ring === 'gradient' ? 'bg-accent-gradient' : 'bg-accent',
        className,
      )}
      style={{ width: size + 5, height: size + 5 }}
    >
      <span className="block h-full w-full rounded-full bg-surface p-[2px]">{inner}</span>
    </span>
  );
}

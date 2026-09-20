'use client';

import Image from 'next/image';
import { cn } from '@/lib/utils/cn';
import { initials } from '@/lib/utils/format';

export interface AvatarProps {
  src?: string | null;
  name?: string | null;
  size?: number;
  online?: boolean;
  className?: string;
}

/** Falls back to initials, because a broken image is worse than no image. */
export function Avatar({ src, name, size = 48, online, className }: AvatarProps) {
  return (
    <span className={cn('relative inline-block shrink-0', className)} style={{ width: size, height: size }}>
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
          className="flex h-full w-full items-center justify-center rounded-full bg-surface-muted text-ink-subtle"
          style={{ fontSize: size * 0.36 }}
        >
          {initials(name)}
        </span>
      )}
      {online ? (
        <span
          className="absolute bottom-0 right-0 block rounded-full border-2 border-surface bg-success"
          style={{ width: size * 0.26, height: size * 0.26 }}
          aria-label="Active recently"
        />
      ) : null}
    </span>
  );
}

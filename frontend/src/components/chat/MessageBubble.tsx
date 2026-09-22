'use client';

import Image from 'next/image';
import { cn } from '@/lib/utils/cn';
import { ImageIcon } from '@/components/ui/icons';
import { durationLabel, messageTimestamp } from '@/lib/utils/format';
import type { Message } from '@/lib/api/types';

export interface MessageBubbleProps {
  message: Message;
  showReadReceipt: boolean;
}

/**
 * One message.
 *
 * <p>System messages (a match notice, a call summary) are centred and unstyled rather than
 * dressed as a bubble - they are not from a person, and pretending otherwise confuses the
 * thread.
 */
export function MessageBubble({ message, showReadReceipt }: MessageBubbleProps) {
  if (message.type === 'SYSTEM' || message.type === 'CALL_SUMMARY') {
    return (
      <li className="my-4 flex justify-center">
        <span className="rounded-pill bg-surface-muted px-3.5 py-1.5 text-[11.5px] font-medium text-ink-subtle ring-1 ring-inset ring-border">
          {message.body}
        </span>
      </li>
    );
  }

  const mine = message.mine;

  return (
    <li className={cn('flex w-full animate-slide-up', mine ? 'justify-end' : 'justify-start')}>
      <div className="max-w-[78%] space-y-1">
        {message.attachments.map((attachment) => (
          <Attachment key={attachment.id} attachment={attachment} mine={mine} />
        ))}

        {message.deleted ? (
          <p className="rounded-2xl bg-surface-muted px-4 py-2.5 text-sm italic text-ink-subtle">
            Message removed
          </p>
        ) : message.body ? (
          <p
            className={cn(
              'whitespace-pre-wrap break-words rounded-2xl px-4 py-2.5 text-[15px] leading-[1.45]',
              mine
                ? 'rounded-br-md bg-accent-gradient text-white shadow-[0_6px_18px_-10px_rgb(var(--accent)/0.9)]'
                : 'rounded-bl-md bg-surface-muted text-ink',
            )}
          >
            {message.body}
          </p>
        ) : null}

        <p
          className={cn(
            'px-1.5 text-[11px] font-medium text-ink-subtle',
            mine ? 'text-right' : 'text-left',
          )}
        >
          {messageTimestamp(message.createdAt)}
          {mine && showReadReceipt ? (message.readAt ? ' · Read' : ' · Sent') : null}
        </p>
      </div>
    </li>
  );
}

function Attachment({
  attachment,
  mine,
}: {
  attachment: Message['attachments'][number];
  mine: boolean;
}) {
  if (attachment.contentType.startsWith('image/')) {
    return (
      <div className="relative h-56 w-56 overflow-hidden rounded-2xl bg-surface-muted ring-1 ring-inset ring-border">
        <Image
          src={attachment.url}
          alt={attachment.fileName ?? 'Attachment'}
          fill
          className="object-cover"
          unoptimized
        />
      </div>
    );
  }

  if (attachment.contentType.startsWith('audio/')) {
    return (
      <div
        className={cn(
          'flex items-center gap-3 rounded-2xl px-3 py-2.5',
          mine ? 'bg-accent-gradient text-white' : 'bg-surface-muted text-ink',
        )}
      >
        <audio controls src={attachment.url} className="h-8 max-w-[200px]">
          <track kind="captions" />
        </audio>
        <span className="text-xs tabular-nums opacity-80">
          {durationLabel(attachment.durationSeconds)}
        </span>
      </div>
    );
  }

  if (attachment.contentType.startsWith('video/')) {
    return (
      <video controls src={attachment.url} className="max-h-72 rounded-2xl">
        <track kind="captions" />
      </video>
    );
  }

  return (
    <a
      href={attachment.url}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-2 rounded-2xl bg-surface-muted px-4 py-2.5 text-sm font-medium text-ink ring-1 ring-inset ring-border transition-colors hover:bg-border/50"
    >
      <ImageIcon size={17} className="shrink-0 text-ink-subtle" />
      {attachment.fileName ?? 'Attachment'}
    </a>
  );
}

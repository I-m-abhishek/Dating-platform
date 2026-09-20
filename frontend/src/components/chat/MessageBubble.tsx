'use client';

import Image from 'next/image';
import { cn } from '@/lib/utils/cn';
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
      <li className="my-3 flex justify-center">
        <span className="rounded-pill bg-surface-muted px-3 py-1 text-xs text-ink-subtle">
          {message.body}
        </span>
      </li>
    );
  }

  const mine = message.mine;

  return (
    <li className={cn('flex w-full', mine ? 'justify-end' : 'justify-start')}>
      <div className="max-w-[78%] space-y-1">
        {message.attachments.map((attachment) => (
          <Attachment key={attachment.id} attachment={attachment} mine={mine} />
        ))}

        {message.deleted ? (
          <p className="rounded-2xl bg-surface-muted px-3.5 py-2 text-sm italic text-ink-subtle">
            Message removed
          </p>
        ) : message.body ? (
          <p
            className={cn(
              'whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2 text-[15px] leading-snug',
              mine
                ? 'rounded-br-md bg-accent text-accent-ink'
                : 'rounded-bl-md bg-surface-muted text-ink',
            )}
          >
            {message.body}
          </p>
        ) : null}

        <p className={cn('px-1 text-[11px] text-ink-subtle', mine ? 'text-right' : 'text-left')}>
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
      <div className="relative h-56 w-56 overflow-hidden rounded-2xl bg-surface-muted">
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
          mine ? 'bg-accent text-accent-ink' : 'bg-surface-muted text-ink',
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
      className="flex items-center gap-2 rounded-2xl bg-surface-muted px-3.5 py-2.5 text-sm text-ink underline-offset-2 hover:underline"
    >
      📎 {attachment.fileName ?? 'Attachment'}
    </a>
  );
}

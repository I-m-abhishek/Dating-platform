'use client';

import { useState, type FormEvent } from 'react';
import Image from 'next/image';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Textarea';
import { Avatar } from '@/components/ui/Avatar';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { usePhotoComments, useCommentQuota } from '@/lib/hooks/useComments';
import { relativeTime, quotaLabel } from '@/lib/utils/format';
import type { Photo } from '@/lib/api/types';

export interface PhotoCommentSheetProps {
  photo: Photo | null;
  onClose: () => void;
}

/**
 * Comment thread for one photo.
 *
 * <p>The remaining allowance is shown before the user types, not after they hit the wall.
 * The number comes from the server ({@code /comments/quota}), so a plan change or a config
 * change is reflected immediately without a release.
 */
export function PhotoCommentSheet({ photo, onClose }: PhotoCommentSheetProps) {
  const [body, setBody] = useState('');
  const { comments, query, create, isPosting, remove } = usePhotoComments(photo?.id ?? null);
  const { data: quota } = useCommentQuota();

  const outOfComments = quota ? !quota.unlimited && quota.remaining <= 0 : false;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!body.trim()) return;
    // A failure here surfaces through usePaywall inside the hook, which opens the
    // upgrade sheet rather than showing a dead-end error.
    await create({ body: body.trim() });
    setBody('');
  }

  return (
    <Sheet open={Boolean(photo)} onClose={onClose} title="Comments" size="tall">
      {photo ? (
        <div className="space-y-5">
          <div className="relative h-40 overflow-hidden rounded-2xl bg-surface-muted">
            <Image src={photo.url} alt="" fill className="object-cover" unoptimized />
          </div>

          {query.isPending ? (
            <Skeleton.List rows={3} />
          ) : comments.length === 0 ? (
            <EmptyState title="No comments yet" description="Be the first to say something." />
          ) : (
            <ul className="space-y-4">
              {comments.map((comment) => (
                <li key={comment.id} className="flex gap-3">
                  <Avatar
                    src={comment.author?.primaryPhotoUrl}
                    name={comment.author?.displayName}
                    size={36}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm">
                      <span className="font-medium text-ink">
                        {comment.author?.displayName ?? 'Someone'}
                      </span>{' '}
                      <span className="text-ink-subtle">{relativeTime(comment.createdAt)}</span>
                    </p>
                    <p className="mt-0.5 text-[15px] leading-snug text-ink-muted">{comment.body}</p>
                    {comment.canDelete ? (
                      <button
                        type="button"
                        onClick={() => remove(comment.id)}
                        className="mt-1 text-xs text-ink-subtle underline-offset-2 hover:underline"
                      >
                        Remove
                      </button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}

          <form onSubmit={onSubmit} className="space-y-2 border-t border-border pt-4">
            <Textarea
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="Say something about this photo"
              rows={3}
              counterMax={300}
              aria-label="Your comment"
            />
            <div className="flex items-center justify-between gap-3">
              <p className="text-xs text-ink-subtle">
                {quota
                  ? quotaLabel(quota.used, quota.limit, quota.unlimited, 'comments')
                  : ' '}
              </p>
              <Button type="submit" size="sm" loading={isPosting} disabled={!body.trim()}>
                {outOfComments ? 'Upgrade to comment' : 'Post'}
              </Button>
            </div>
          </form>
        </div>
      ) : null}
    </Sheet>
  );
}

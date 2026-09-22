'use client';

import Link from 'next/link';
import type { CSSProperties } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LinkButton } from '@/components/ui/LinkButton';
import { ChatIcon } from '@/components/ui/icons';
import { useConversations } from '@/lib/hooks/useChat';
import { conversationTimestamp } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

function MessagesPage() {
  const query = useConversations();
  const conversations = query.data?.items ?? [];
  const unreadTotal = conversations.reduce((total, item) => total + item.unreadCount, 0);

  return (
    <>
      <TopBar
        title="Messages"
        subtitle={unreadTotal > 0 ? `${unreadTotal} unread` : undefined}
      />

      <div className="p-4">
        {query.isPending ? (
          <Skeleton.List rows={6} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : conversations.length === 0 ? (
          <EmptyState
            icon={<ChatIcon size={30} />}
            title="No conversations yet"
            description="Once you match, your chats show up here."
            action={
              <LinkButton href="/home" variant="outline">
                Find someone
              </LinkButton>
            }
          />
        ) : (
          <ul className="-mx-2">
            {conversations.map((conversation, index) => {
              const unread = conversation.unreadCount > 0;

              return (
                <li
                  key={conversation.id}
                  className="stagger"
                  style={{ '--i': Math.min(index, 8) } as CSSProperties}
                >
                  <Link
                    href={`/messages/${conversation.id}`}
                    className="flex items-center gap-3.5 rounded-2xl px-2 py-2.5 transition-colors hover:bg-surface-muted"
                  >
                    <Avatar
                      src={conversation.participant.primaryPhotoUrl}
                      name={conversation.participant.displayName}
                      size={54}
                      online={conversation.participant.recentlyActive}
                      // An unread thread gets the gradient halo, so the list scans in one pass.
                      ring={unread ? 'gradient' : 'none'}
                    />

                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline justify-between gap-2">
                        <p className="truncate text-[15px] font-semibold text-ink">
                          {conversation.participant.displayName}
                        </p>
                        <span
                          className={
                            unread
                              ? 'shrink-0 text-[11px] font-bold text-accent'
                              : 'shrink-0 text-[11px] font-medium text-ink-subtle'
                          }
                        >
                          {conversationTimestamp(conversation.lastMessageAt)}
                        </span>
                      </div>
                      <p
                        className={
                          unread
                            ? 'truncate text-sm font-medium text-ink'
                            : 'truncate text-sm text-ink-muted'
                        }
                      >
                        {conversation.lastMessagePreview ?? 'Say hello'}
                      </p>
                    </div>

                    {unread ? <Badge tone="accent">{conversation.unreadCount}</Badge> : null}
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(MessagesPage);

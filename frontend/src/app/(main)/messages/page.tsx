'use client';

import Link from 'next/link';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useConversations } from '@/lib/hooks/useChat';
import { conversationTimestamp } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

function MessagesPage() {
  const query = useConversations();
  const conversations = query.data?.items ?? [];

  return (
    <>
      <TopBar title="Messages" />

      <div className="p-4">
        {query.isPending ? (
          <Skeleton.List rows={6} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : conversations.length === 0 ? (
          <EmptyState
            title="No conversations yet"
            description="Once you match, your chats show up here."
          />
        ) : (
          <ul className="divide-y divide-border">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <Link href={`/messages/${conversation.id}`} className="flex items-center gap-3 py-3">
                  <Avatar
                    src={conversation.participant.primaryPhotoUrl}
                    name={conversation.participant.displayName}
                    size={52}
                    online={conversation.participant.recentlyActive}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-baseline justify-between gap-2">
                      <p className="truncate text-[15px] font-medium text-ink">
                        {conversation.participant.displayName}
                      </p>
                      <span className="shrink-0 text-xs text-ink-subtle">
                        {conversationTimestamp(conversation.lastMessageAt)}
                      </span>
                    </div>
                    <p className="truncate text-sm text-ink-muted">
                      {conversation.lastMessagePreview ?? 'Say hello'}
                    </p>
                  </div>
                  {conversation.unreadCount > 0 ? (
                    <Badge tone="accent">{conversation.unreadCount}</Badge>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(MessagesPage);

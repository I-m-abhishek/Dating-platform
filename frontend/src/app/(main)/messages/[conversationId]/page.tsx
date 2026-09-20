'use client';

import { useEffect, useRef } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { MessageBubble } from '@/components/chat/MessageBubble';
import { MessageComposer } from '@/components/chat/MessageComposer';
import { CallOverlay } from '@/components/chat/CallOverlay';
import { useConversation } from '@/lib/hooks/useChat';
import { useCall } from '@/lib/hooks/useCall';
import { useEntitlements } from '@/lib/hooks/useEntitlements';

/**
 * One chat thread.
 *
 * <p>The list is rendered in {@code flex-col-reverse} with newest first. That is the trick
 * that makes a chat stick to the bottom without a scroll-to-bottom effect on every render,
 * and it makes "load older" a natural append instead of a scroll-position rescue.
 */
function ConversationPage() {
  const params = useParams<{ conversationId: string }>();
  const conversationId = params.conversationId;

  const {
    conversation,
    conversationQuery,
    historyQuery,
    messages,
    hasMore,
    loadOlder,
    send,
    markRead,
    notifyTyping,
    otherIsTyping,
  } = useConversation(conversationId);

  const call = useCall(conversationId);
  const { has } = useEntitlements();
  const readReceipts = has('READ_RECEIPTS');
  const markedRead = useRef(false);

  // Opening the thread clears the unread badge, once.
  useEffect(() => {
    if (!markedRead.current && messages.length > 0) {
      markedRead.current = true;
      markRead();
    }
  }, [messages.length, markRead]);

  const participant = conversation?.participant;

  return (
    <div className="flex h-[100dvh] flex-col">
      <TopBar
        showBack
        title={participant?.displayName ?? 'Chat'}
        subtitle={otherIsTyping ? 'typing…' : participant?.recentlyActive ? 'Active recently' : undefined}
        action={
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Start a voice call"
              onClick={() => void call.start('VOICE')}
              disabled={conversation?.status !== 'ACTIVE'}
            >
              ☎
            </Button>
            {participant?.userId ? (
              <Link
                href={`/u/${participant.userId}`}
                aria-label={`View ${participant.displayName} profile`}
                className="rounded-full"
              >
                <Avatar src={participant.primaryPhotoUrl} name={participant.displayName} size={32} />
              </Link>
            ) : null}
          </div>
        }
      />

      <div className="flex flex-1 flex-col-reverse overflow-y-auto px-4 py-3">
        {historyQuery.isPending ? (
          <Skeleton.List rows={5} />
        ) : messages.length === 0 ? (
          <EmptyState
            title="Say something"
            description="A question about their prompts beats 'hey' every time."
          />
        ) : (
          <>
            <ul className="flex flex-col-reverse gap-2">
              {messages.map((message) => (
                <MessageBubble key={message.id} message={message} showReadReceipt={readReceipts} />
              ))}
            </ul>

            {hasMore ? (
              <div className="flex justify-center py-3">
                <Button variant="ghost" size="sm" onClick={() => void loadOlder()}>
                  Load earlier messages
                </Button>
              </div>
            ) : null}
          </>
        )}
      </div>

      {conversationQuery.isPending ? null : (
        <MessageComposer
          sendingState={
            conversation?.sendingState ?? {
              canSend: false,
              remainingOpeners: 0,
              openerLimit: 0,
              message: 'Loading…',
            }
          }
          onSend={send}
          onTyping={notifyTyping}
        />
      )}

      <CallOverlay
        phase={call.phase}
        peerName={participant?.displayName}
        peerPhotoUrl={participant?.primaryPhotoUrl}
        elapsed={call.elapsed}
        muted={call.muted}
        remoteAudioRef={call.remoteAudioRef}
        onAccept={() => void call.accept()}
        onDecline={() => void call.decline()}
        onHangUp={() => void call.hangUp()}
        onToggleMute={call.toggleMute}
      />
    </div>
  );
}

export default compose(withErrorBoundary, withAuth)(ConversationPage);

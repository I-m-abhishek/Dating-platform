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
import { PhoneIcon, VideoIcon } from '@/components/ui/icons';
import { useConversation } from '@/lib/hooks/useChat';
import { useCallControls } from '@/providers/CallProvider';
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

  // The call itself (and its overlay) lives app-wide so incoming calls ring on any screen.
  const call = useCallControls();
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
  const canCall = conversation?.status === 'ACTIVE' && call.phase === 'idle';

  return (
    <div className="flex h-[100dvh] flex-col">
      <TopBar
        showBack
        title={participant?.displayName ?? 'Chat'}
        subtitle={otherIsTyping ? 'typing…' : participant?.recentlyActive ? 'Active recently' : undefined}
        leading={
          participant?.userId ? (
            <Link
              href={`/u/${participant.userId}`}
              aria-label={`View ${participant.displayName} profile`}
              className="rounded-full transition-transform duration-200 ease-snap hover:scale-105"
            >
              <Avatar
                src={participant.primaryPhotoUrl}
                name={participant.displayName}
                size={38}
                online={participant.recentlyActive}
              />
            </Link>
          ) : null
        }
        action={
          <div className="flex items-center gap-0.5">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Start a voice call"
              onClick={() => void call.start(conversationId, 'VOICE')}
              disabled={!canCall}
            >
              <PhoneIcon size={19} />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Start a video call"
              onClick={() => void call.start(conversationId, 'VIDEO')}
              disabled={!canCall}
            >
              <VideoIcon size={21} />
            </Button>
          </div>
        }
      />

      <div className="flex flex-1 flex-col-reverse overflow-y-auto px-4 py-4">
        {historyQuery.isPending ? (
          <Skeleton.List rows={5} />
        ) : messages.length === 0 ? (
          <EmptyState
            title="Say something"
            description="A question about their prompts beats 'hey' every time."
          />
        ) : (
          <>
            <ul className="flex flex-col-reverse gap-2.5">
              {/*
                First child of a reversed column, so it renders at the visual bottom - right
                where the message being typed is about to appear.
              */}
              {otherIsTyping ? (
                <li className="flex justify-start pt-1">
                  <span
                    className="flex items-center gap-1 rounded-2xl rounded-bl-md bg-surface-muted px-4 py-3"
                    aria-label={`${participant?.displayName ?? 'They'} are typing`}
                  >
                    {[0, 1, 2].map((dot) => (
                      <span
                        key={dot}
                        aria-hidden
                        className="h-1.5 w-1.5 animate-typing-dot rounded-full bg-ink-subtle"
                        style={{ animationDelay: `${dot * 0.15}s` }}
                      />
                    ))}
                  </span>
                </li>
              ) : null}

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
    </div>
  );
}

export default compose(withErrorBoundary, withAuth)(ConversationPage);

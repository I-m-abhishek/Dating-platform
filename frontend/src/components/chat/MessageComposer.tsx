'use client';

import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Button } from '@/components/ui/Button';
import { PlusIcon, SendIcon } from '@/components/ui/icons';
import { chatApi } from '@/lib/api/endpoints';
import { useUiStore } from '@/lib/stores/uiStore';
import { messageOf } from '@/lib/api/errors';
import type { SendingState } from '@/lib/api/types';

export interface MessageComposerProps {
  sendingState: SendingState;
  onSend: (body: string, attachmentIds: string[]) => Promise<void>;
  onTyping: (typing: boolean) => void;
  disabled?: boolean;
}

/**
 * The composer, and where the opener rule becomes visible.
 *
 * <p>When the caller opened the conversation and the other person has not replied, the
 * server caps how many messages they may send. It reports that in {@code sendingState}, so
 * the input can show "2 more until they reply" and then close cleanly - instead of letting
 * someone type a paragraph and rejecting it on submit.
 */
export function MessageComposer({ sendingState, onSend, onTyping, disabled }: MessageComposerProps) {
  const [body, setBody] = useState('');
  const [attachmentIds, setAttachmentIds] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const toast = useUiStore((state) => state.toast);

  const blocked = disabled || !sendingState.canSend;
  const canSubmit = !blocked && !sending && (body.trim().length > 0 || attachmentIds.length > 0);

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!canSubmit) return;
    setSending(true);
    try {
      await onSend(body.trim(), attachmentIds);
      setBody('');
      setAttachmentIds([]);
      onTyping(false);
    } finally {
      setSending(false);
    }
  }

  async function onPickFile(files: FileList | null) {
    const file = files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const asset = await chatApi.uploadAttachment(file);
      setAttachmentIds((current) => [...current, asset.id]);
    } catch (error) {
      toast({ title: messageOf(error), tone: 'error' });
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends, Shift+Enter breaks the line - the convention everywhere else.
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void submit();
    }
  }

  if (blocked) {
    return (
      <div className="glass border-t border-border px-6 py-5 text-center">
        <p className="mx-auto max-w-sm text-sm leading-relaxed text-ink-muted">
          {sendingState.message ?? 'You cannot send messages in this conversation.'}
        </p>
      </div>
    );
  }

  return (
    <form
      onSubmit={submit}
      className="glass border-t border-border px-3 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] pt-3"
    >
      {sendingState.remainingOpeners >= 0 ? (
        <p className="mb-2 flex items-center gap-1.5 px-1.5 text-[11.5px] font-medium text-ink-subtle">
          <span aria-hidden className="h-1 w-1 rounded-full bg-accent" />
          {sendingState.remainingOpeners} more message
          {sendingState.remainingOpeners === 1 ? '' : 's'} until they reply
        </p>
      ) : null}

      {attachmentIds.length > 0 ? (
        <p className="mb-2 px-1.5 text-[11.5px] font-semibold text-accent">
          {attachmentIds.length} attachment{attachmentIds.length === 1 ? '' : 's'} ready
        </p>
      ) : null}

      <div className="flex items-end gap-2">
        <input
          ref={fileInput}
          type="file"
          accept="image/*,audio/*,video/mp4,application/pdf"
          className="hidden"
          onChange={(event) => void onPickFile(event.target.files)}
        />
        <Button
          type="button"
          variant="secondary"
          size="icon"
          aria-label="Add an attachment"
          loading={uploading}
          onClick={() => fileInput.current?.click()}
        >
          <PlusIcon size={19} />
        </Button>

        <textarea
          value={body}
          rows={1}
          onChange={(event) => {
            setBody(event.target.value);
            onTyping(event.target.value.length > 0);
          }}
          onBlur={() => onTyping(false)}
          onKeyDown={onKeyDown}
          placeholder="Message"
          aria-label="Message"
          maxLength={2000}
          className="max-h-32 min-h-[44px] flex-1 resize-none rounded-2xl border border-border bg-surface px-4 py-3 text-[15px] leading-snug text-ink transition-[border-color,box-shadow] placeholder:text-ink-subtle focus:border-accent focus:shadow-[0_0_0_4px_rgb(var(--accent)/0.13)] focus:outline-none"
        />

        <Button type="submit" size="icon" aria-label="Send" loading={sending} disabled={!canSubmit}>
          <SendIcon size={18} />
        </Button>
      </div>
    </form>
  );
}

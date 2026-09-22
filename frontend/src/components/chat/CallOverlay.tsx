'use client';

import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { PhoneIcon } from '@/components/ui/icons';
import { durationLabel } from '@/lib/utils/format';
import type { CallPhase } from '@/lib/hooks/useCall';
import type { MutableRefObject } from 'react';

export interface CallOverlayProps {
  phase: CallPhase;
  peerName?: string | null;
  peerPhotoUrl?: string | null;
  elapsed: number;
  muted: boolean;
  remoteAudioRef: MutableRefObject<HTMLAudioElement | null>;
  onAccept: () => void;
  onDecline: () => void;
  onHangUp: () => void;
  onToggleMute: () => void;
}

const STATUS: Record<CallPhase, string> = {
  idle: '',
  outgoing: 'Calling…',
  incoming: 'Incoming call',
  connecting: 'Connecting…',
  active: '',
  ended: 'Call ended',
};

/**
 * Full-screen call UI.
 *
 * <p>The audio element is always mounted while the overlay is up: mounting it lazily loses
 * the first second of audio, because the remote track arrives before React has a node to
 * attach it to.
 */
export function CallOverlay({
  phase,
  peerName,
  peerPhotoUrl,
  elapsed,
  muted,
  remoteAudioRef,
  onAccept,
  onDecline,
  onHangUp,
  onToggleMute,
}: CallOverlayProps) {
  if (phase === 'idle') return null;

  // Only a call that has not connected yet is still waiting on someone to answer it.
  const ringing = phase === 'outgoing' || phase === 'incoming' || phase === 'connecting';

  return (
    <div className="fixed inset-0 z-[70] flex animate-fade-in flex-col items-center justify-between overflow-hidden bg-canvas-dark px-6 py-16 text-white">
      {/* Enough light to keep the screen from reading as a crash. */}
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="absolute -top-40 left-1/2 h-[30rem] w-[30rem] -translate-x-1/2 rounded-full bg-accent/30 blur-[110px]" />
        <div className="absolute -bottom-44 right-0 h-[26rem] w-[26rem] rounded-full bg-accent-2/25 blur-[110px]" />
      </div>

      <audio ref={remoteAudioRef} autoPlay className="hidden">
        <track kind="captions" />
      </audio>

      <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-6 text-center">
        <div className="relative">
          {ringing ? (
            <span
              aria-hidden
              className="absolute -inset-3 animate-pulse-ring rounded-full bg-accent opacity-40"
            />
          ) : null}
          <Avatar src={peerPhotoUrl} name={peerName} size={124} className="relative" />
        </div>

        <div className="space-y-1.5">
          <p className="font-display text-[28px] font-semibold tracking-[-0.02em]">
            {peerName ?? 'Unknown'}
          </p>
          <p className="text-sm font-medium tabular-nums text-white/60">
            {phase === 'active' ? durationLabel(elapsed) : STATUS[phase]}
          </p>
        </div>
      </div>

      <div className="relative z-10 flex w-full max-w-xs items-center justify-center gap-4">
        {phase === 'incoming' ? (
          <>
            <Button variant="danger" size="lg" fullWidth onClick={onDecline}>
              Decline
            </Button>
            <Button size="lg" fullWidth onClick={onAccept} leftIcon={<PhoneIcon size={18} />}>
              Accept
            </Button>
          </>
        ) : (
          <>
            <Button
              variant="glass"
              size="lg"
              onClick={onToggleMute}
              aria-pressed={muted}
              className="flex-1"
            >
              {muted ? 'Unmute' : 'Mute'}
            </Button>
            <Button variant="danger" size="lg" onClick={onHangUp} className="flex-1">
              End
            </Button>
          </>
        )}
      </div>
    </div>
  );
}

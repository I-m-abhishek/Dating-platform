'use client';

import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
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

  return (
    <div className="fixed inset-0 z-[70] flex flex-col items-center justify-between bg-ink px-6 py-16 text-bg">
      <audio ref={remoteAudioRef} autoPlay className="hidden">
        <track kind="captions" />
      </audio>

      <div className="flex flex-1 flex-col items-center justify-center gap-5 text-center">
        <Avatar src={peerPhotoUrl} name={peerName} size={112} />
        <div className="space-y-1">
          <p className="text-xl font-semibold">{peerName ?? 'Unknown'}</p>
          <p className="text-sm opacity-70">
            {phase === 'active' ? durationLabel(elapsed) : STATUS[phase]}
          </p>
        </div>
      </div>

      <div className="flex w-full max-w-xs items-center justify-center gap-4">
        {phase === 'incoming' ? (
          <>
            <Button variant="danger" size="lg" fullWidth onClick={onDecline}>
              Decline
            </Button>
            <Button size="lg" fullWidth onClick={onAccept}>
              Accept
            </Button>
          </>
        ) : (
          <>
            <Button
              variant="secondary"
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

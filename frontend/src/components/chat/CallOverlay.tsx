'use client';

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import {
  FlipCameraIcon,
  MicIcon,
  MicOffIcon,
  MinimizeIcon,
  PhoneIcon,
  VideoIcon,
  VideoOffIcon,
} from '@/components/ui/icons';
import { cn } from '@/lib/utils/cn';
import { durationLabel } from '@/lib/utils/format';
import { useDraggable } from '@/lib/hooks/useDraggable';
import type { CallPhase, CameraFacing } from '@/lib/hooks/useCall';

export interface CallOverlayProps {
  phase: CallPhase;
  type?: 'VOICE' | 'VIDEO';
  peerName?: string | null;
  peerPhotoUrl?: string | null;
  elapsed: number;
  muted: boolean;
  cameraOff: boolean;
  /** Their video is not showing: camera switched off, or no frames arriving. */
  remoteVideoOff: boolean;
  /** They said they switched their camera off (as opposed to frames not arriving yet). */
  remoteCameraOff: boolean;
  remoteMuted: boolean;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  facing: CameraFacing;
  canSwitchCamera: boolean;
  onAccept: () => void;
  onDecline: () => void;
  onHangUp: () => void;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  onSwitchCamera: () => void;
}

/**
 * The call UI, for voice and video: full screen, or minimized to a floating window so the
 * rest of the app stays usable during a call.
 *
 * <p><b>One tree for both modes.</b> Minimizing only changes classes; the element that plays
 * the other person's audio and video stays in the same place in the tree. If it moved,
 * React would recreate it and the call would go silent for a moment on every switch.
 */
export function CallOverlay(props: CallOverlayProps) {
  const {
    phase,
    type = 'VOICE',
    peerName,
    peerPhotoUrl,
    elapsed,
    cameraOff,
    localStream,
    remoteStream,
    remoteVideoOff,
    remoteCameraOff,
    remoteMuted,
    facing,
  } = props;

  const [minimized, setMinimized] = useState(false);
  const [localAspect, setLocalAspect] = useState(9 / 16);

  // A new call always opens full screen, and an incoming one must be answered there.
  useEffect(() => {
    if (phase === 'idle' || phase === 'incoming') setMinimized(false);
  }, [phase]);

  const video = type === 'VIDEO';
  const isMini = minimized && phase !== 'idle' && phase !== 'incoming';
  // Only a call that has not connected yet is still waiting on someone to answer it.
  const ringing = phase === 'outgoing' || phase === 'incoming' || phase === 'connecting';
  const localHasVideo = Boolean(
    localStream?.getVideoTracks().some((track) => track.readyState === 'live'),
  );
  const showRemoteVideo = video && phase === 'active' && !remoteVideoOff;
  // Before they pick up, your own camera fills the screen, the way a phone does it.
  const localFullScreen = video && ringing && localHasVideo;
  const showPip =
    !isMini && video && (phase === 'active' || (phase === 'connecting' && !localFullScreen));

  // The minimized window stays clear of the bottom nav bar on phones.
  const floating = useDraggable<HTMLDivElement>({
    enabled: isMini,
    snap: 'sides',
    insetTop: 4,
    insetBottom: 84,
  });
  // Your self-view tile: below the minimize button, above the call controls.
  const selfView = useDraggable<HTMLDivElement>({
    enabled: showPip,
    snap: 'corners',
    insetTop: 44,
    insetBottom: 140,
  });

  const expand = useCallback(() => {
    if (!floating.consumeDrag()) setMinimized(false);
  }, [floating]);

  if (phase === 'idle') return null;

  const name = peerName ?? '';
  const status =
    phase === 'active'
      ? durationLabel(elapsed)
      : phase === 'outgoing'
        ? 'Calling…'
        : phase === 'incoming'
          ? video
            ? 'Incoming video call'
            : 'Incoming call'
          : phase === 'connecting'
            ? 'Connecting…'
            : 'Call ended';
  const canMinimize = phase === 'outgoing' || phase === 'connecting' || phase === 'active';
  const mirror = facing === 'user';

  return (
    <div
      ref={floating.ref}
      style={floating.style}
      {...floating.handlers}
      role={isMini ? 'button' : 'dialog'}
      aria-label={isMini ? `Call with ${name || 'your match'} - tap to open` : 'Call'}
      tabIndex={isMini ? 0 : undefined}
      onClick={isMini ? expand : undefined}
      onKeyDown={
        isMini
          ? (event: KeyboardEvent) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setMinimized(false);
              }
            }
          : undefined
      }
      className={cn(
        'flex animate-fade-in overflow-hidden bg-canvas-dark text-white',
        isMini
          ? cn(
              'z-[80] select-none rounded-2xl shadow-2xl ring-1 ring-white/15',
              video ? 'h-44 w-[124px] flex-col sm:h-56 sm:w-40' : 'w-[272px] items-center',
            )
          : 'fixed inset-0 z-[70] flex-col',
      )}
    >
      {isMini ? null : (
        <div aria-hidden className="pointer-events-none absolute inset-0">
          <div className="absolute -top-40 left-1/2 h-[30rem] w-[30rem] -translate-x-1/2 rounded-full bg-accent/30 blur-[110px]" />
          <div className="absolute -bottom-44 right-0 h-[26rem] w-[26rem] rounded-full bg-accent-2/25 blur-[110px]" />
        </div>
      )}

      {/* The other person's media. Same slot in every mode - see the class comment. */}
      {video ? (
        <div
          className={cn(
            'pointer-events-none absolute inset-0 bg-black transition-opacity duration-300',
            showRemoteVideo ? 'opacity-100' : 'opacity-0',
          )}
        >
          <FittedVideo stream={remoteStream} />
        </div>
      ) : (
        <StreamAudio stream={remoteStream} />
      )}

      {localFullScreen ? (
        <div className="pointer-events-none absolute inset-0 bg-black">
          <FittedVideo stream={localStream} muted mirror={mirror} />
          <div aria-hidden className="absolute inset-0 bg-black/35" />
        </div>
      ) : null}

      {isMini ? (
        <MiniCall
          {...props}
          video={video}
          name={name}
          status={status}
          showRemoteVideo={showRemoteVideo}
          localFullScreen={localFullScreen}
        />
      ) : (
        <>
          {/* Top scrim keeps the name legible over bright video. */}
          {showRemoteVideo || localFullScreen ? (
            <div
              aria-hidden
              className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-black/60 to-transparent"
            />
          ) : null}

          {canMinimize ? (
            <button
              type="button"
              aria-label="Minimize call"
              onClick={() => setMinimized(true)}
              className="glass-dark absolute left-4 top-4 z-30 flex h-10 w-10 items-center justify-center rounded-full border border-white/15 text-white transition-transform duration-200 ease-snap hover:bg-black/55 active:scale-95"
            >
              <MinimizeIcon size={18} />
            </button>
          ) : null}

          <div className="relative z-10 flex flex-1 flex-col px-6 pb-12 pt-16">
            {showRemoteVideo ? (
              <div className="space-y-1.5">
                <p className="font-display text-xl font-semibold tracking-[-0.01em]">{name}</p>
                <p className="text-sm font-medium tabular-nums text-white/75">{status}</p>
                {remoteMuted ? (
                  <PeerBadge icon={<MicOffIcon size={14} />}>Muted</PeerBadge>
                ) : null}
              </div>
            ) : (
              <div className="flex flex-1 flex-col items-center justify-center gap-6 text-center">
                <div className="relative">
                  {ringing ? (
                    <span
                      aria-hidden
                      className="absolute -inset-3 animate-pulse-ring rounded-full bg-accent opacity-40"
                    />
                  ) : null}
                  <Avatar src={peerPhotoUrl} name={peerName} size={124} className="relative" />
                </div>

                <div className="flex flex-col items-center gap-2">
                  <p className="font-display text-[28px] font-semibold tracking-[-0.02em]">
                    {name}
                  </p>
                  <p className="text-sm font-medium tabular-nums text-white/70">{status}</p>
                  {phase === 'active' ? (
                    <div className="flex flex-wrap justify-center gap-2">
                      {video && remoteVideoOff ? (
                        <PeerBadge icon={<VideoOffIcon size={14} />}>
                          {remoteCameraOff
                            ? `${name || 'They'} turned their camera off`
                            : 'Waiting for video…'}
                        </PeerBadge>
                      ) : null}
                      {remoteMuted ? (
                        <PeerBadge icon={<MicOffIcon size={14} />}>
                          {`${name || 'They'} muted their mic`}
                        </PeerBadge>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            )}

            {showRemoteVideo ? <div className="flex-1" /> : null}

            <Controls {...props} video={video} />
          </div>

          {showPip ? (
            <div
              ref={selfView.ref}
              style={{
                ...selfView.style,
                aspectRatio: cameraOff || !localHasVideo ? '3 / 4' : String(localAspect),
              }}
              {...selfView.handlers}
              aria-label="Your camera - drag to move"
              className={cn(
                'z-20 select-none overflow-hidden rounded-2xl bg-black shadow-2xl ring-1 ring-white/20',
                localAspect >= 1 && !cameraOff ? 'w-40 sm:w-52' : 'w-28 sm:w-36',
              )}
            >
              {cameraOff || !localHasVideo ? (
                <div className="flex h-full w-full flex-col items-center justify-center gap-1.5 bg-white/5 text-white/70">
                  <VideoOffIcon size={22} />
                  <span className="text-[11px] font-medium">Camera off</span>
                </div>
              ) : (
                <StreamVideo
                  stream={localStream}
                  muted
                  onAspect={setLocalAspect}
                  className={cn(
                    'pointer-events-none h-full w-full object-cover',
                    mirror && '-scale-x-100',
                  )}
                />
              )}
              {props.muted ? (
                <span className="absolute bottom-1.5 left-1.5 rounded-full bg-black/60 p-1">
                  <MicOffIcon size={13} />
                </span>
              ) : null}
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

/** The contents of the minimized window. Its buttons must not also expand the window. */
function MiniCall({
  video,
  name,
  status,
  showRemoteVideo,
  localFullScreen,
  peerName,
  peerPhotoUrl,
  phase,
  muted,
  remoteVideoOff,
  remoteMuted,
  onToggleMute,
  onHangUp,
}: CallOverlayProps & {
  video: boolean;
  name: string;
  status: string;
  showRemoteVideo: boolean;
  localFullScreen: boolean;
}) {
  const only = (action: () => void) => (event: MouseEvent) => {
    event.stopPropagation();
    action();
  };
  const ended = phase === 'ended';

  const buttons = (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label={muted ? 'Unmute' : 'Mute'}
        aria-pressed={muted}
        disabled={ended}
        onClick={only(onToggleMute)}
        className={cn(
          'flex h-9 w-9 items-center justify-center rounded-full transition-transform active:scale-95 disabled:opacity-50',
          muted ? 'bg-white text-canvas-dark' : 'bg-white/15 text-white hover:bg-white/25',
        )}
      >
        {muted ? <MicOffIcon size={17} /> : <MicIcon size={17} />}
      </button>
      <button
        type="button"
        aria-label="End call"
        disabled={ended}
        onClick={only(onHangUp)}
        className="flex h-9 w-9 items-center justify-center rounded-full bg-danger text-white transition-transform active:scale-95 disabled:opacity-50"
      >
        <PhoneIcon size={17} className="rotate-[135deg]" />
      </button>
    </div>
  );

  if (!video) {
    return (
      <div className="relative flex w-full items-center gap-3 p-2.5">
        <Avatar src={peerPhotoUrl} name={peerName} size={40} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{name}</p>
          <p className="truncate text-xs tabular-nums text-white/65">
            {remoteMuted && phase === 'active' ? `${status} · Muted` : status}
          </p>
        </div>
        {buttons}
      </div>
    );
  }

  return (
    <>
      {showRemoteVideo || localFullScreen ? null : (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-2 text-center">
          <Avatar src={peerPhotoUrl} name={peerName} size={52} />
          {phase === 'active' && remoteVideoOff ? (
            <span className="flex items-center gap-1 text-[11px] text-white/70">
              <VideoOffIcon size={12} /> Camera off
            </span>
          ) : null}
        </div>
      )}

      <div className="pointer-events-none absolute inset-x-0 top-0 flex items-center justify-between gap-1 bg-gradient-to-b from-black/65 to-transparent px-2 pb-4 pt-1.5">
        <span className="truncate text-[11px] font-semibold tabular-nums">{status}</span>
        {remoteMuted ? <MicOffIcon size={13} className="shrink-0 text-white/80" /> : null}
      </div>

      <div className="absolute inset-x-0 bottom-0 flex justify-center bg-gradient-to-t from-black/70 to-transparent pb-2 pt-5">
        {buttons}
      </div>
    </>
  );
}

function PeerBadge({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-black/45 px-3 py-1 text-xs font-medium text-white/90 ring-1 ring-inset ring-white/15 backdrop-blur">
      {icon}
      {children}
    </span>
  );
}

function Controls({
  phase,
  video,
  muted: isMuted,
  cameraOff,
  canSwitchCamera,
  onAccept,
  onDecline,
  onHangUp,
  onToggleMute,
  onToggleCamera,
  onSwitchCamera,
}: CallOverlayProps & { video: boolean }) {
  if (phase === 'incoming') {
    return (
      <div className="mx-auto flex w-full max-w-xs items-center justify-center gap-4">
        <Button variant="danger" size="lg" fullWidth onClick={onDecline}>
          Decline
        </Button>
        <Button
          size="lg"
          fullWidth
          onClick={onAccept}
          leftIcon={video ? <VideoIcon size={18} /> : <PhoneIcon size={18} />}
        >
          Accept
        </Button>
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-sm items-start justify-center gap-5">
      <RoundControl
        label={isMuted ? 'Unmute' : 'Mute'}
        active={isMuted}
        onClick={onToggleMute}
        disabled={phase === 'ended'}
      >
        {isMuted ? <MicOffIcon size={24} /> : <MicIcon size={24} />}
      </RoundControl>

      {video ? (
        <RoundControl
          label={cameraOff ? 'Camera on' : 'Camera off'}
          active={cameraOff}
          onClick={onToggleCamera}
          disabled={phase === 'ended'}
        >
          {cameraOff ? <VideoOffIcon size={24} /> : <VideoIcon size={24} />}
        </RoundControl>
      ) : null}

      {video && canSwitchCamera ? (
        <RoundControl
          label="Flip"
          onClick={onSwitchCamera}
          disabled={phase === 'ended' || cameraOff}
        >
          <FlipCameraIcon size={24} />
        </RoundControl>
      ) : null}

      <RoundControl label="End" tone="danger" onClick={onHangUp} disabled={phase === 'ended'}>
        <PhoneIcon size={24} className="rotate-[135deg]" />
      </RoundControl>
    </div>
  );
}

function RoundControl({
  label,
  children,
  onClick,
  active = false,
  tone = 'glass',
  disabled,
}: {
  label: string;
  children: ReactNode;
  onClick: () => void;
  active?: boolean;
  tone?: 'glass' | 'danger';
  disabled?: boolean;
}) {
  return (
    <div className="flex flex-col items-center gap-1.5">
      <button
        type="button"
        aria-label={label}
        aria-pressed={tone === 'glass' ? active : undefined}
        onClick={onClick}
        disabled={disabled}
        className={cn(
          'flex h-14 w-14 items-center justify-center rounded-full transition-all duration-200 ease-snap active:scale-95 disabled:opacity-50',
          tone === 'danger'
            ? 'bg-danger text-white shadow-card hover:brightness-105'
            : active
              ? 'bg-white text-canvas-dark'
              : 'glass-dark border border-white/15 text-white hover:bg-black/55',
        )}
      >
        {children}
      </button>
      <span className="text-[11px] font-medium text-white/75">{label}</span>
    </div>
  );
}

/**
 * A full-screen video that never crops someone down to their forehead.
 *
 * <p>{@code object-cover} is right when the video and the screen share a shape (a phone
 * showing another phone). When they do not - a portrait phone camera on a landscape PC
 * window, or a landscape webcam on a phone - cover zooms in until only the middle strip is
 * left. In that case the whole frame is shown ({@code contain}) over a blurred, muted copy
 * of itself, so the empty sides are filled instead of black.
 */
function FittedVideo({
  stream,
  muted = false,
  mirror = false,
}: {
  stream: MediaStream | null;
  muted?: boolean;
  mirror?: boolean;
}) {
  const boxRef = useRef<HTMLDivElement | null>(null);
  const [videoAspect, setVideoAspect] = useState<number | null>(null);
  const [boxAspect, setBoxAspect] = useState<number | null>(null);

  useEffect(() => {
    const box = boxRef.current;
    if (!box) return;
    const measure = () => {
      if (box.clientHeight > 0) setBoxAspect(box.clientWidth / box.clientHeight);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    return () => observer.disconnect();
  }, []);

  const contain = shouldContain(videoAspect, boxAspect);

  return (
    <div ref={boxRef} className="absolute inset-0 overflow-hidden">
      {contain ? (
        <StreamVideo
          stream={stream}
          muted
          className="absolute inset-0 h-full w-full scale-110 object-cover opacity-60 blur-2xl"
        />
      ) : null}
      <StreamVideo
        stream={stream}
        muted={muted}
        onAspect={setVideoAspect}
        className={cn(
          'absolute inset-0 h-full w-full',
          contain ? 'object-contain' : 'object-cover',
          mirror && '-scale-x-100',
        )}
      />
    </div>
  );
}

/** Contain when cropping to fill would lose a big part of the picture. */
function shouldContain(video: number | null, box: number | null): boolean {
  if (!video || !box) return false;
  const differentShape = video >= 1 !== box >= 1;
  const cropRatio = Math.max(video, box) / Math.min(video, box);
  return differentShape || cropRatio > 1.4;
}

/** Attaches a MediaStream to a <video>; srcObject has no JSX attribute. */
function StreamVideo({
  stream,
  muted = false,
  className,
  onAspect,
}: {
  stream: MediaStream | null;
  muted?: boolean;
  className?: string;
  /** Reports width / height whenever the incoming frame size changes (e.g. phone rotated). */
  onAspect?: (aspect: number) => void;
}) {
  const ref = useRef<HTMLVideoElement | null>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element || element.srcObject === stream) return;
    element.srcObject = stream;
    if (stream) {
      void element.play().catch(() => {
        // Autoplay can be blocked until the user interacts; the call/accept tap counts.
      });
    }
  }, [stream]);

  const report = useCallback(() => {
    const element = ref.current;
    if (element && element.videoWidth > 0 && element.videoHeight > 0) {
      onAspect?.(element.videoWidth / element.videoHeight);
    }
  }, [onAspect]);

  // playsInline keeps iOS from hijacking the video into its own full-screen player.
  return (
    <video
      ref={ref}
      autoPlay
      playsInline
      muted={muted}
      onLoadedMetadata={report}
      onResize={report}
      className={className}
    />
  );
}

function StreamAudio({ stream }: { stream: MediaStream | null }) {
  const ref = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element || element.srcObject === stream) return;
    element.srcObject = stream;
    if (stream) void element.play().catch(() => undefined);
  }, [stream]);

  return (
    <audio ref={ref} autoPlay className="hidden">
      <track kind="captions" />
    </audio>
  );
}

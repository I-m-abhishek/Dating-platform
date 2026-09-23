'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { callApi, chatApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { destinations, socket } from '@/lib/ws/socket';
import { useUiStore } from '@/lib/stores/uiStore';
import { showSystemNotification } from '@/lib/utils/systemNotifications';
import type { Call } from '@/lib/api/types';

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'connecting' | 'active' | 'ended';
export type CameraFacing = 'user' | 'environment';

interface SignalEvent {
  event: string;
  call?: Call;
  callId?: string;
  type?: 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | 'HANGUP' | 'MEDIA_STATE';
  payload?: string;
}

/** A failure the user can act on, as opposed to a bug. */
class CallSetupError extends Error {}

const VIDEO_CONSTRAINTS = (facing: CameraFacing): MediaTrackConstraints => ({
  facingMode: facing,
  width: { ideal: 1280 },
  height: { ideal: 720 },
});

/**
 * The microphone and camera are only exposed to secure pages. iOS Safari goes further than
 * desktop browsers and removes {@code navigator.mediaDevices} entirely on plain http, which
 * is what produced "undefined is not an object (evaluating
 * 'navigator.mediaDevices.getUserMedia')".
 */
function assertMediaAvailable(): void {
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
    throw new CallSetupError(
      window.isSecureContext
        ? 'This browser does not support calls.'
        : 'Calls need a secure connection. Open the app over https:// to use your microphone.',
    );
  }
}

function describeMediaError(error: unknown, video: boolean): CallSetupError {
  const name = error instanceof DOMException ? error.name : '';
  const device = video ? 'camera and microphone' : 'microphone';
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return new CallSetupError(`Access to your ${device} is blocked. Allow it in your browser settings.`);
  }
  if (name === 'NotFoundError' || name === 'OverconstrainedError') {
    return new CallSetupError(`No ${video ? 'camera' : 'microphone'} was found on this device.`);
  }
  if (name === 'NotReadableError') {
    return new CallSetupError(`Your ${device} is being used by another app.`);
  }
  return new CallSetupError(`Could not access your ${device}.`);
}

/**
 * Microphone, plus the camera for a video call.
 *
 * <p>A video call on a device without a usable camera degrades to voice rather than failing;
 * {@code videoFallback} tells the caller that happened.
 */
async function getLocalMedia(
  video: boolean,
  facing: CameraFacing,
): Promise<{ stream: MediaStream; videoFallback: boolean }> {
  assertMediaAvailable();
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: video ? VIDEO_CONSTRAINTS(facing) : false,
    });
    return { stream, videoFallback: false };
  } catch (error) {
    const name = error instanceof DOMException ? error.name : '';
    const cameraProblem =
      name === 'NotFoundError' || name === 'OverconstrainedError' || name === 'NotReadableError';
    if (video && cameraProblem) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        return { stream, videoFallback: true };
      } catch (audioError) {
        throw describeMediaError(audioError, false);
      }
    }
    throw describeMediaError(error, video);
  }
}

/**
 * Voice and video calling over WebRTC. Mounted ONCE for the whole app by
 * {@code CallProvider}, so an incoming call rings on whatever screen the user is on - not
 * only when they happen to have that conversation open.
 *
 * <p>The server only relays signalling; audio and video flow directly between the two
 * browsers.
 *
 * <p>Flow: the caller creates the session over REST and grabs the microphone (and camera),
 * then waits. The callee grabs theirs and builds a peer connection BEFORE accepting, so by
 * the time the caller hears CALL_ACCEPTED and sends its offer, there is something ready to
 * answer it. An offer that still arrives early is buffered rather than discarded.
 *
 * <p>Camera on/off and front/back switching use {@code replaceTrack}, which changes what is
 * sent without renegotiating the connection.
 */
export function useCall() {
  const toast = useUiStore((state) => state.toast);
  const queryClient = useQueryClient();

  const [phase, setPhase] = useState<CallPhase>('idle');
  const [call, setCall] = useState<Call | null>(null);
  // Known the moment the caller taps, before the server has created the call - so the
  // overlay can show who is being called instead of "Unknown".
  const [dialingConversationId, setDialingConversationId] = useState<string | null>(null);
  const [muted, setMuted] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [cameraOff, setCameraOff] = useState(false);
  // No frames arriving on their video track (not started yet, or stalled).
  const [remoteTrackMuted, setRemoteTrackMuted] = useState(true);
  // What the other side SAYS about its camera and mic (MEDIA_STATE). Browsers do not
  // reliably report a camera switched off - the last frame just freezes - so this is the
  // source of truth, and the track state is only a fallback.
  const [remoteCameraOff, setRemoteCameraOff] = useState(false);
  const [remoteMuted, setRemoteMuted] = useState(false);
  const [facing, setFacing] = useState<CameraFacing>('user');
  const [canSwitchCamera, setCanSwitchCamera] = useState(false);

  const phaseRef = useRef<CallPhase>('idle');
  const callRef = useRef<Call | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const facingRef = useRef<CameraFacing>('user');
  const pendingCandidates = useRef<RTCIceCandidateInit[]>([]);
  const pendingOffer = useRef<RTCSessionDescriptionInit | null>(null);
  const resetTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const updatePhase = useCallback((next: CallPhase) => {
    phaseRef.current = next;
    setPhase(next);
  }, []);

  const updateCall = useCallback((next: Call | null) => {
    callRef.current = next;
    setCall(next);
    if (!next) setDialingConversationId(null);
  }, []);

  const teardown = useCallback(() => {
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    localStreamRef.current = null;
    remoteStreamRef.current = null;
    peerRef.current?.close();
    peerRef.current = null;
    pendingCandidates.current = [];
    pendingOffer.current = null;
    facingRef.current = 'user';
    setLocalStream(null);
    setRemoteStream(null);
    setElapsed(0);
    setMuted(false);
    setCameraOff(false);
    setRemoteTrackMuted(true);
    setRemoteCameraOff(false);
    setRemoteMuted(false);
    setFacing('user');
    setCanSwitchCamera(false);
  }, []);

  /** Shows "Call ended" briefly, then clears the overlay. */
  const finish = useCallback(() => {
    teardown();
    updatePhase('ended');
    if (resetTimer.current) clearTimeout(resetTimer.current);
    resetTimer.current = setTimeout(() => {
      updatePhase('idle');
      updateCall(null);
    }, 1200);
  }, [teardown, updatePhase, updateCall]);

  const flushCandidates = useCallback(async (peer: RTCPeerConnection) => {
    const queued = pendingCandidates.current;
    pendingCandidates.current = [];
    for (const candidate of queued) {
      await peer.addIceCandidate(candidate).catch(() => undefined);
    }
  }, []);

  const answerOffer = useCallback(
    async (peer: RTCPeerConnection, callId: string, offer: RTCSessionDescriptionInit) => {
      await peer.setRemoteDescription(offer);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      socket.publish(destinations.signal, {
        callId,
        type: 'ANSWER',
        payload: JSON.stringify(answer),
      });
      await flushCandidates(peer);
    },
    [flushCandidates],
  );

  /** Tells the other side whether our mic and camera are currently on. */
  const announceMediaState = useCallback(() => {
    const current = callRef.current;
    const stream = localStreamRef.current;
    if (!current || !stream) return;
    socket.publish(destinations.signal, {
      callId: current.id,
      type: 'MEDIA_STATE',
      payload: JSON.stringify({
        audio: stream.getAudioTracks().some((track) => track.enabled),
        video: stream.getVideoTracks().some((track) => track.enabled && track.readyState === 'live'),
      }),
    });
  }, []);

  const createPeer = useCallback(
    async (session: Call): Promise<RTCPeerConnection> => {
      // Media first: if it is unavailable there is no point opening a connection.
      const wantsVideo = session.type === 'VIDEO';
      const { stream, videoFallback } = await getLocalMedia(wantsVideo, facingRef.current);
      if (videoFallback) {
        toast({ title: 'No camera available - continuing with audio only', tone: 'default' });
      }

      const peer = new RTCPeerConnection({
        iceServers: session.iceServers.map((server) => ({
          urls: server.urls,
          username: server.username,
          credential: server.credential,
        })),
      });
      localStreamRef.current = stream;
      setLocalStream(stream);
      setCameraOff(wantsVideo && stream.getVideoTracks().length === 0);
      stream.getTracks().forEach((track) => peer.addTrack(track, stream));

      // A video call always negotiates a video line, even when this side has no camera, so
      // the other person's video still arrives.
      if (wantsVideo && stream.getVideoTracks().length === 0) {
        peer.addTransceiver('video', { direction: 'recvonly' });
      }
      if (wantsVideo) {
        void navigator.mediaDevices
          .enumerateDevices()
          .then((devices) =>
            setCanSwitchCamera(devices.filter((device) => device.kind === 'videoinput').length > 1),
          )
          .catch(() => undefined);
      }

      peer.onicecandidate = (event) => {
        if (event.candidate) {
          socket.publish(destinations.signal, {
            callId: session.id,
            type: 'ICE_CANDIDATE',
            payload: JSON.stringify(event.candidate),
          });
        }
      };

      peer.ontrack = (event) => {
        // Safari can deliver a track without its stream; collect such tracks into one stream
        // so audio and video still play from the same element.
        const remote = event.streams[0] ?? remoteStreamRef.current ?? new MediaStream();
        if (!remote.getTracks().includes(event.track)) remote.addTrack(event.track);
        remoteStreamRef.current = remote;
        setRemoteStream(remote);

        if (event.track.kind === 'video') {
          // The other side turning their camera off stops the frames, which mutes the track
          // here - show their photo rather than a frozen or black frame.
          setRemoteTrackMuted(event.track.muted);
          event.track.onmute = () => setRemoteTrackMuted(true);
          event.track.onunmute = () => setRemoteTrackMuted(false);
          event.track.onended = () => setRemoteTrackMuted(true);
        }
      };

      peer.onconnectionstatechange = () => {
        if (peer !== peerRef.current) return;
        if (peer.connectionState === 'connected') {
          updatePhase('active');
          announceMediaState();
        }
        if (peer.connectionState === 'failed') {
          toast({ title: 'The call could not connect', tone: 'error' });
          void callApi.hangUp(session.id).catch(() => undefined);
          finish();
        }
      };

      peerRef.current = peer;
      return peer;
    },
    [announceMediaState, finish, toast, updatePhase],
  );

  const reportFailure = useCallback(
    (error: unknown, fallback: string) => {
      toast({
        title: error instanceof CallSetupError ? error.message : fallback,
        tone: 'error',
      });
    },
    [toast],
  );

  const start = useCallback(
    async (conversationId: string, type: 'VOICE' | 'VIDEO' = 'VOICE') => {
      if (phaseRef.current !== 'idle') return;
      let session: Call | null = null;
      try {
        setDialingConversationId(conversationId);
        updatePhase('outgoing');
        session = await callApi.start(conversationId, type);
        updateCall(session);
        await createPeer(session);
        // The offer goes out on CALL_ACCEPTED, once the other side is ready for it.
      } catch (error) {
        if (session) void callApi.hangUp(session.id).catch(() => undefined);
        teardown();
        updatePhase('idle');
        updateCall(null);
        reportFailure(error, 'Could not start the call');
      }
    },
    [createPeer, reportFailure, teardown, updateCall, updatePhase],
  );

  const accept = useCallback(async () => {
    const current = callRef.current;
    if (!current || phaseRef.current !== 'incoming') return;
    updatePhase('connecting');
    try {
      const peer = peerRef.current ?? (await createPeer(current));
      const session = await callApi.accept(current.id);
      updateCall(session);

      const offer = pendingOffer.current;
      pendingOffer.current = null;
      if (offer) await answerOffer(peer, session.id, offer);
    } catch (error) {
      void callApi.decline(current.id).catch(() => undefined);
      reportFailure(error, 'Could not answer the call');
      finish();
    }
  }, [answerOffer, createPeer, finish, reportFailure, updateCall, updatePhase]);

  const decline = useCallback(async () => {
    const current = callRef.current;
    teardown();
    updatePhase('idle');
    updateCall(null);
    if (current) await callApi.decline(current.id).catch(() => undefined);
  }, [teardown, updateCall, updatePhase]);

  const hangUp = useCallback(async () => {
    const current = callRef.current;
    finish();
    if (current) await callApi.hangUp(current.id).catch(() => undefined);
  }, [finish]);

  const toggleMute = useCallback(() => {
    const track = localStreamRef.current?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setMuted(!track.enabled);
    announceMediaState();
  }, [announceMediaState]);

  /** The sender for the video line, found by what it RECEIVES so it survives a null track. */
  const videoSender = useCallback(
    () =>
      peerRef.current
        ?.getTransceivers()
        .find((transceiver) => transceiver.receiver.track?.kind === 'video')?.sender ?? null,
    [],
  );

  /** Swaps the local camera track everywhere it is used: the connection and the preview. */
  const replaceLocalVideo = useCallback(
    async (next: MediaStreamTrack | null) => {
      const current = localStreamRef.current;
      if (!current) return;
      await videoSender()?.replaceTrack(next);
      current.getVideoTracks().forEach((track) => track.stop());
      // A new stream object, so the preview element notices the change.
      const updated = new MediaStream([...current.getAudioTracks(), ...(next ? [next] : [])]);
      localStreamRef.current = updated;
      setLocalStream(updated);
    },
    [videoSender],
  );

  /**
   * Camera off really releases the camera (the light goes out) and stops sending frames,
   * which the other side sees as their video track muting.
   */
  const toggleCamera = useCallback(async () => {
    if (callRef.current?.type !== 'VIDEO') return;
    if (!cameraOff) {
      await replaceLocalVideo(null);
      setCameraOff(true);
      announceMediaState();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: VIDEO_CONSTRAINTS(facingRef.current),
      });
      await replaceLocalVideo(stream.getVideoTracks()[0] ?? null);
      setCameraOff(false);
      announceMediaState();
    } catch (error) {
      toast({ title: describeMediaError(error, true).message, tone: 'error' });
    }
  }, [announceMediaState, cameraOff, replaceLocalVideo, toast]);

  /** Front/back camera on phones. */
  const switchCamera = useCallback(async () => {
    if (callRef.current?.type !== 'VIDEO' || cameraOff) return;
    const next: CameraFacing = facingRef.current === 'user' ? 'environment' : 'user';
    try {
      // Release the current camera first: most phones cannot open both at once.
      localStreamRef.current?.getVideoTracks().forEach((track) => track.stop());
      const stream = await navigator.mediaDevices.getUserMedia({ video: VIDEO_CONSTRAINTS(next) });
      await replaceLocalVideo(stream.getVideoTracks()[0] ?? null);
      facingRef.current = next;
      setFacing(next);
    } catch {
      toast({ title: 'Could not switch camera', tone: 'error' });
    }
  }, [cameraOff, replaceLocalVideo, toast]);

  // Signalling and call lifecycle events.
  useEffect(() => {
    const unsubscribe = socket.subscribe(destinations.calls, (payload) => {
      void (async () => {
        const event = payload as SignalEvent;
        const current = callRef.current;

        if (event.event === 'INCOMING_CALL' && event.call) {
          // Already on a call: the server rejects a second one, so this should not happen.
          if (phaseRef.current !== 'idle' && phaseRef.current !== 'ended') return;
          if (resetTimer.current) clearTimeout(resetTimer.current);
          teardown();
          updateCall(event.call);
          updatePhase('incoming');

          const conversation = await queryClient
            .fetchQuery({
              queryKey: queryKeys.chat.conversation(event.call.conversationId),
              queryFn: () => chatApi.conversation(event.call!.conversationId),
            })
            .catch(() => undefined);
          const name = conversation?.participant?.displayName ?? 'Someone';
          const video = event.call.type === 'VIDEO';
          void showSystemNotification(video ? 'Incoming video call' : 'Incoming call', {
            body: video ? `${name} is video calling you` : `${name} is calling you`,
            tag: `call-${event.call.id}`,
            url: `/messages/${event.call.conversationId}`,
            requireInteraction: true,
          });
          return;
        }

        // Everything below belongs to one specific call; ignore frames for any other.
        const eventCallId = event.callId ?? event.call?.id;
        if (!current || (eventCallId && eventCallId !== current.id)) return;

        if (event.event === 'CALL_ACCEPTED') {
          const peer = peerRef.current;
          if (!peer) return;
          updatePhase('connecting');
          try {
            const offer = await peer.createOffer();
            await peer.setLocalDescription(offer);
            socket.publish(destinations.signal, {
              callId: current.id,
              type: 'OFFER',
              payload: JSON.stringify(offer),
            });
          } catch {
            toast({ title: 'The call could not connect', tone: 'error' });
            void callApi.hangUp(current.id).catch(() => undefined);
            finish();
          }
          return;
        }

        if (event.event === 'CALL_ENDED') {
          finish();
          return;
        }

        if (event.event !== 'SIGNAL' || !event.payload) return;
        const peer = peerRef.current;

        if (event.type === 'OFFER') {
          const offer = JSON.parse(event.payload) as RTCSessionDescriptionInit;
          if (peer && phaseRef.current !== 'incoming') {
            await answerOffer(peer, current.id, offer);
          } else {
            // Not accepted yet - hold it until they do.
            pendingOffer.current = offer;
          }
          return;
        }

        if (event.type === 'ANSWER' && peer) {
          await peer.setRemoteDescription(JSON.parse(event.payload) as RTCSessionDescriptionInit);
          await flushCandidates(peer);
          return;
        }

        if (event.type === 'MEDIA_STATE') {
          try {
            const state = JSON.parse(event.payload) as { audio?: boolean; video?: boolean };
            setRemoteMuted(state.audio === false);
            setRemoteCameraOff(state.video === false);
          } catch {
            // Malformed state is ignored; the track-level fallback still applies.
          }
          return;
        }

        if (event.type === 'ICE_CANDIDATE') {
          const candidate = JSON.parse(event.payload) as RTCIceCandidateInit;
          if (peer?.remoteDescription) {
            await peer.addIceCandidate(candidate).catch(() => undefined);
          } else {
            pendingCandidates.current.push(candidate);
          }
        }
      })();
    });

    return unsubscribe;
  }, [answerOffer, finish, flushCandidates, queryClient, teardown, toast, updateCall, updatePhase]);

  // Call timer.
  useEffect(() => {
    if (phase !== 'active') return;
    const interval = setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
    return () => clearInterval(interval);
  }, [phase]);

  useEffect(
    () => () => {
      if (resetTimer.current) clearTimeout(resetTimer.current);
      teardown();
    },
    [teardown],
  );

  return {
    phase,
    call,
    conversationId: call?.conversationId ?? dialingConversationId,
    muted,
    elapsed,
    localStream,
    remoteStream,
    cameraOff,
    remoteVideoOff: remoteCameraOff || remoteTrackMuted,
    remoteCameraOff,
    remoteMuted,
    facing,
    canSwitchCamera,
    start,
    accept,
    decline,
    hangUp,
    toggleMute,
    toggleCamera,
    switchCamera,
  };
}

export type CallControls = ReturnType<typeof useCall>;

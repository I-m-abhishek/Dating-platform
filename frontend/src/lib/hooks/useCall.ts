'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { callApi } from '@/lib/api/endpoints';
import { destinations, socket } from '@/lib/ws/socket';
import { useUiStore } from '@/lib/stores/uiStore';
import type { Call } from '@/lib/api/types';

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'connecting' | 'active' | 'ended';

interface SignalEvent {
  event: string;
  call?: Call;
  callId?: string;
  type?: 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | 'HANGUP';
  payload?: string;
}

/**
 * Voice and video calling over WebRTC.
 *
 * <p>The server only relays signalling; audio and video flow directly between the two
 * browsers. That keeps media off our infrastructure entirely - cheaper, lower latency, and
 * a much smaller privacy surface.
 *
 * <p>Flow: caller creates the session over REST, gets ICE servers back, builds the offer and
 * publishes it. The callee answers, both sides trickle ICE candidates, and the connection
 * establishes. Hanging up is REST again so the summary lands in the thread.
 */
export function useCall(conversationId?: string) {
  const toast = useUiStore((state) => state.toast);

  const [phase, setPhase] = useState<CallPhase>('idle');
  const [call, setCall] = useState<Call | null>(null);
  const [muted, setMuted] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  const peerRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const pendingCandidates = useRef<RTCIceCandidateInit[]>([]);

  const teardown = useCallback(() => {
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    localStreamRef.current = null;
    peerRef.current?.close();
    peerRef.current = null;
    pendingCandidates.current = [];
    setElapsed(0);
    setMuted(false);
  }, []);

  const createPeer = useCallback(
    async (session: Call): Promise<RTCPeerConnection> => {
      const peer = new RTCPeerConnection({
        iceServers: session.iceServers.map((server) => ({
          urls: server.urls,
          username: server.username,
          credential: server.credential,
        })),
      });

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: session.type === 'VIDEO',
      });
      localStreamRef.current = stream;
      stream.getTracks().forEach((track) => peer.addTrack(track, stream));

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
        const [remoteStream] = event.streams;
        if (remoteAudioRef.current && remoteStream) {
          remoteAudioRef.current.srcObject = remoteStream;
          void remoteAudioRef.current.play().catch(() => {
            // Autoplay can be blocked until the user interacts; the accept tap counts.
          });
        }
      };

      peer.onconnectionstatechange = () => {
        if (peer.connectionState === 'connected') setPhase('active');
        if (peer.connectionState === 'failed') {
          toast({ title: 'The call could not connect', tone: 'error' });
          setPhase('ended');
          teardown();
        }
      };

      peerRef.current = peer;
      return peer;
    },
    [teardown, toast],
  );

  const start = useCallback(
    async (type: 'VOICE' | 'VIDEO' = 'VOICE') => {
      if (!conversationId) return;
      try {
        setPhase('outgoing');
        const session = await callApi.start(conversationId, type);
        setCall(session);

        const peer = await createPeer(session);
        const offer = await peer.createOffer();
        await peer.setLocalDescription(offer);
        socket.publish(destinations.signal, {
          callId: session.id,
          type: 'OFFER',
          payload: JSON.stringify(offer),
        });
      } catch (error) {
        setPhase('idle');
        teardown();
        toast({ title: 'Could not start the call', tone: 'error' });
        throw error;
      }
    },
    [conversationId, createPeer, teardown, toast],
  );

  const accept = useCallback(async () => {
    if (!call) return;
    setPhase('connecting');
    const session = await callApi.accept(call.id);
    setCall(session);
    if (!peerRef.current) {
      await createPeer(session);
    }
  }, [call, createPeer]);

  const decline = useCallback(async () => {
    if (call) await callApi.decline(call.id);
    setPhase('idle');
    setCall(null);
    teardown();
  }, [call, teardown]);

  const hangUp = useCallback(async () => {
    if (call) await callApi.hangUp(call.id);
    setPhase('ended');
    teardown();
    setTimeout(() => setPhase('idle'), 1200);
  }, [call, teardown]);

  const toggleMute = useCallback(() => {
    const track = localStreamRef.current?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    setMuted(!track.enabled);
  }, []);

  // Signalling and call lifecycle events.
  useEffect(() => {
    const unsubscribe = socket.subscribe(destinations.calls, async (payload) => {
      const event = payload as SignalEvent;

      if (event.event === 'INCOMING_CALL' && event.call) {
        setCall(event.call);
        setPhase('incoming');
        return;
      }
      if (event.event === 'CALL_ENDED') {
        setPhase('ended');
        teardown();
        setTimeout(() => setPhase('idle'), 1200);
        return;
      }
      if (event.event !== 'SIGNAL' || !event.payload) return;

      const peer = peerRef.current;

      if (event.type === 'OFFER' && peer) {
        await peer.setRemoteDescription(JSON.parse(event.payload) as RTCSessionDescriptionInit);
        const answer = await peer.createAnswer();
        await peer.setLocalDescription(answer);
        socket.publish(destinations.signal, {
          callId: event.callId,
          type: 'ANSWER',
          payload: JSON.stringify(answer),
        });
        // Candidates that arrived before the remote description was set.
        pendingCandidates.current.forEach((candidate) => void peer.addIceCandidate(candidate));
        pendingCandidates.current = [];
        return;
      }

      if (event.type === 'ANSWER' && peer) {
        await peer.setRemoteDescription(JSON.parse(event.payload) as RTCSessionDescriptionInit);
        return;
      }

      if (event.type === 'ICE_CANDIDATE') {
        const candidate = JSON.parse(event.payload) as RTCIceCandidateInit;
        if (peer?.remoteDescription) {
          await peer.addIceCandidate(candidate);
        } else {
          pendingCandidates.current.push(candidate);
        }
      }
    });

    return unsubscribe;
  }, [teardown]);

  // Call timer.
  useEffect(() => {
    if (phase !== 'active') return;
    const interval = setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
    return () => clearInterval(interval);
  }, [phase]);

  useEffect(() => teardown, [teardown]);

  return { phase, call, muted, elapsed, remoteAudioRef, start, accept, decline, hangUp, toggleMute };
}

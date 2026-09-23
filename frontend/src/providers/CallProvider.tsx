'use client';

import { createContext, useContext, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { chatApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useCall, type CallControls } from '@/lib/hooks/useCall';
import { CallOverlay } from '@/components/chat/CallOverlay';

const CallContext = createContext<CallControls | null>(null);

/**
 * Owns the one call the user can be on, for the whole app.
 *
 * <p>It used to live inside the chat screen, which meant the person being called only heard
 * it ring if they already had that exact conversation open. Here it listens from every
 * screen and draws the overlay above all of them.
 */
export function CallProvider({ children }: { children: ReactNode }) {
  const controls = useCall();
  const conversationId = controls.conversationId;

  // Same key the chat screen uses, so the name and photo are usually already cached.
  const { data: conversation } = useQuery({
    queryKey: queryKeys.chat.conversation(conversationId ?? ''),
    queryFn: () => chatApi.conversation(conversationId!),
    enabled: Boolean(conversationId),
  });
  const participant = conversation?.participant;

  return (
    <CallContext.Provider value={controls}>
      {children}
      <CallOverlay
        phase={controls.phase}
        type={controls.call?.type}
        peerName={participant?.displayName}
        peerPhotoUrl={participant?.primaryPhotoUrl}
        elapsed={controls.elapsed}
        muted={controls.muted}
        cameraOff={controls.cameraOff}
        remoteVideoOff={controls.remoteVideoOff}
        remoteCameraOff={controls.remoteCameraOff}
        remoteMuted={controls.remoteMuted}
        localStream={controls.localStream}
        remoteStream={controls.remoteStream}
        facing={controls.facing}
        canSwitchCamera={controls.canSwitchCamera}
        onAccept={() => void controls.accept()}
        onDecline={() => void controls.decline()}
        onHangUp={() => void controls.hangUp()}
        onToggleMute={controls.toggleMute}
        onToggleCamera={() => void controls.toggleCamera()}
        onSwitchCamera={() => void controls.switchCamera()}
      />
    </CallContext.Provider>
  );
}

export function useCallControls(): CallControls {
  const context = useContext(CallContext);
  if (!context) throw new Error('useCallControls must be used inside <CallProvider>');
  return context;
}

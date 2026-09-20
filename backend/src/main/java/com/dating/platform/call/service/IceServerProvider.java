package com.dating.platform.call.service;

import com.dating.platform.call.dto.CallResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies the ICE servers the browser needs to negotiate a peer connection.
 *
 * <p>STUN alone is enough for most networks. A TURN relay is required for the minority
 * behind symmetric NAT - configure {@code app.webrtc.turn.*} to enable it. Credentials are
 * returned per request so they can later be made short-lived without a client change.
 */
@Component
public class IceServerProvider {

    @Value("${app.webrtc.stun-urls:stun:stun.l.google.com:19302}")
    private List<String> stunUrls;

    @Value("${app.webrtc.turn.url:}")
    private String turnUrl;

    @Value("${app.webrtc.turn.username:}")
    private String turnUsername;

    @Value("${app.webrtc.turn.credential:}")
    private String turnCredential;

    public List<CallResponse.IceServer> iceServers() {
        List<CallResponse.IceServer> servers = new ArrayList<>();
        servers.add(new CallResponse.IceServer(stunUrls, null, null));
        if (turnUrl != null && !turnUrl.isBlank()) {
            servers.add(new CallResponse.IceServer(List.of(turnUrl), turnUsername, turnCredential));
        }
        return servers;
    }
}

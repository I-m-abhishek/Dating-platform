package com.dating.platform.call.dto;

import com.dating.platform.call.entity.CallSession;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StartCallRequest")
public record StartCallRequest(CallSession.CallType type) {

    public CallSession.CallType typeOrDefault() {
        return type == null ? CallSession.CallType.VOICE : type;
    }
}

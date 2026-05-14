package com.cqie.deepcover.ai.record;

import java.util.List;

/**
 * Java 调 Python 发言决策接口时的请求体。
 */
public record AiSpeechDecisionRequest(
        String roomCode,
        String aiPlayerId,
        List<AiPlayerContext> players,
        List<AiChatMessageContext> messages
) {
}

package com.cqie.deepcover.ai.record;

import java.util.List;

/**
 * Java 调 Python 投票决策接口时的请求体。
 */
public record AiVoteDecisionRequest(
        String roomCode,
        int roundNumber,
        String aiPlayerId,
        List<AiPlayerContext> players,
        List<AiChatMessageContext> messages,
        List<String> candidatePlayerIds
) {
}

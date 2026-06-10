package com.cqie.deepcover.agent.event;

/**
 * Java 推送给 Agent 的关键词描述轮次开始事件。
 */
public record AgentWordRoundStartedPayload(
        String roomCode,
        int roundNumber,
        String currentPlayerId,
        Integer currentNumber
) {
}

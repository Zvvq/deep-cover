package com.cqie.deepcover.agent.internal.record;

/**
 * Python Agent 请求 Java 提交 AI 投票。
 */
public record AgentVoteCommand(
        String aiPlayerId,
        String targetPlayerId,
        String reason
) {
}

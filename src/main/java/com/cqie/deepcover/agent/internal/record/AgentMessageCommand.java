package com.cqie.deepcover.agent.internal.record;

/**
 * Python Agent 请求 Java 发送 AI 聊天消息。
 */
public record AgentMessageCommand(
        String aiPlayerId,
        String content
) {
}

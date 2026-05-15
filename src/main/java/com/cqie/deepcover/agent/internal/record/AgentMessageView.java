package com.cqie.deepcover.agent.internal.record;

import java.time.Instant;

/**
 * Agent 查询聊天记录时看到的消息视图。
 */
public record AgentMessageView(
        String messageId,
        String senderPlayerId,
        String content,
        Instant createdAt
) {
}

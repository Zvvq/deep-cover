package com.cqie.deepcover.agent.event;

import java.time.Instant;

/**
 * 聊天消息事件内容。
 */
public record AgentChatMessagePayload(
        String messageId,
        String senderPlayerId,
        String content,
        Instant createdAt
) {
}

package com.cqie.deepcover.ai.record;

import java.time.Instant;

/**
 * 传给 AI 决策服务的聊天上下文。
 */
public record AiChatMessageContext(
        String senderPlayerId,
        String content,
        Instant createdAt
) {
}

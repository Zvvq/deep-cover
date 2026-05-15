package com.cqie.deepcover.agent.internal.record;

import java.util.List;

/**
 * Agent 查询最近聊天记录的响应。
 */
public record AgentRecentMessagesResponse(
        String roomCode,
        List<AgentMessageView> messages
) {
}

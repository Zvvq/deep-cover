package com.cqie.deepcover.agent.internal.record;

/**
 * Agent 提交关键词描述的内部命令。
 */
public record AgentWordDescriptionCommand(
        String aiPlayerId,
        String content
) {
}

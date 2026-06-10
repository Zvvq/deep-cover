package com.cqie.deepcover.agent.event;

import com.cqie.deepcover.word.record.WordDescriptionEntry;

/**
 * Java 推送给 Agent 的关键词描述提交事件。
 */
public record AgentWordDescriptionSubmittedPayload(
        int roundNumber,
        WordDescriptionEntry description
) {
}

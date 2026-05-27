package com.cqie.deepcover.agent.event;

import com.cqie.deepcover.topic.record.TopicSnapshot;

/**
 * 新一轮聊天开始事件内容。
 */
public record AgentRoundStartedPayload(int roundNumber, TopicSnapshot topic) {
}

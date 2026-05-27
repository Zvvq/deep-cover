package com.cqie.deepcover.agent.event;

import com.cqie.deepcover.topic.record.TopicSnapshot;

import java.util.List;

/**
 * 房间开始时推给 Agent 的内容。
 */
public record AgentRoomStartedPayload(
        String roomCode,
        TopicSnapshot topic,
        List<String> aiPlayerIds
) {
}

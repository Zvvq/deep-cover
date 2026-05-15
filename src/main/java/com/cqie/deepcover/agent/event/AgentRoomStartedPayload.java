package com.cqie.deepcover.agent.event;

import java.util.List;

/**
 * 房间开始时推给 Agent 的内容。
 */
public record AgentRoomStartedPayload(
        String roomCode,
        List<String> aiPlayerIds
) {
}

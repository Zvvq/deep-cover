package com.cqie.deepcover.agent.internal.record;

import com.cqie.deepcover.room.enums.RoomStatus;

import java.util.List;

/**
 * Agent 查询房间状态的响应。
 */
public record AgentRoomStateResponse(
        String roomCode,
        RoomStatus status,
        int roundNumber,
        long aliveHumanCount,
        long aliveAiCount,
        List<AgentPlayerView> players
) {
}

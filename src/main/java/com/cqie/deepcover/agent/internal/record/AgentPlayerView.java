package com.cqie.deepcover.agent.internal.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * Agent 查询房间状态时看到的玩家视图，不包含 token。
 */
public record AgentPlayerView(
        String playerId,
        PlayerType type,
        boolean alive,
        boolean host
) {
}

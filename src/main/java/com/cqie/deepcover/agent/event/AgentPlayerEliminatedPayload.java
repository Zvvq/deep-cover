package com.cqie.deepcover.agent.event;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * 玩家被淘汰事件内容。
 */
public record AgentPlayerEliminatedPayload(
        String playerId,
        PlayerType playerType
) {
}

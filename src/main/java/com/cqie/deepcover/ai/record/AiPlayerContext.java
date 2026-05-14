package com.cqie.deepcover.ai.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * 传给 AI 决策服务的玩家信息，不包含 token。
 */
public record AiPlayerContext(
        String playerId,
        PlayerType type,
        boolean alive
) {
}

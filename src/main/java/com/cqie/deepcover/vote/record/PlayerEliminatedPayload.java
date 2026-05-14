package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * 广播给前端的淘汰结果。
 */
public record PlayerEliminatedPayload(
        String roomCode,
        int roundNumber,
        String playerId,
        PlayerType playerType
) {
}

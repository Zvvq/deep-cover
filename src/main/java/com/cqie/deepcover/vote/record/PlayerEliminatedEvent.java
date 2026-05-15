package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * Spring 内部事件：玩家被淘汰。
 */
public record PlayerEliminatedEvent(
        String roomCode,
        int roundNumber,
        String playerId,
        PlayerType playerType
) {
}

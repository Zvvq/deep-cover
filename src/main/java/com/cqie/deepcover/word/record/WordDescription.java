package com.cqie.deepcover.word.record;

import com.cqie.deepcover.room.enums.PlayerType;

import java.time.Instant;

/**
 * 玩家在关键词卧底某一轮提交的描述。
 */
public record WordDescription(
        String roomCode,
        int roundNumber,
        String playerId,
        Integer number,
        String color,
        PlayerType playerType,
        String content,
        Instant createdAt
) {
}

package com.cqie.deepcover.word.record;

import com.cqie.deepcover.room.enums.PlayerType;

import java.time.Instant;

/**
 * 对前端可见的单条描述。
 */
public record WordDescriptionEntry(
        String playerId,
        Integer number,
        String color,
        PlayerType playerType,
        String content,
        Instant createdAt
) {
    public static WordDescriptionEntry from(WordDescription description) {
        return new WordDescriptionEntry(
                description.playerId(),
                description.number(),
                description.color(),
                description.playerType(),
                description.content(),
                description.createdAt()
        );
    }
}

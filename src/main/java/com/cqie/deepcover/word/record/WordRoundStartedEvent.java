package com.cqie.deepcover.word.record;

/**
 * 关键词卧底进入某一轮描述阶段的 Java 内部事件。
 */
public record WordRoundStartedEvent(
        String roomCode,
        int roundNumber,
        String currentPlayerId,
        Integer currentNumber
) {
}

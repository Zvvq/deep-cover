package com.cqie.deepcover.word.record;

/**
 * 关键词卧底进入新一轮描述阶段时广播的事件内容。
 */
public record WordRoundStartedPayload(
        String roomCode,
        int roundNumber,
        String currentPlayerId,
        Integer currentNumber
) {
}

package com.cqie.deepcover.word.record;

/**
 * 关键词卧底玩家提交描述后的 Java 内部事件。
 */
public record WordDescriptionSubmittedEvent(
        String roomCode,
        int roundNumber,
        WordDescriptionEntry description
) {
}

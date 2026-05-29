package com.cqie.deepcover.word.record;

import java.util.List;

/**
 * 关键词描述阶段的当前状态。
 *
 * <p>currentPlayerId/currentNumber 表示当前应该描述的玩家；全部描述完成后这两个字段为 null。</p>
 */
public record WordDescriptionSnapshot(
        String roomCode,
        int roundNumber,
        String currentPlayerId,
        Integer currentNumber,
        boolean roundComplete,
        List<WordDescriptionEntry> descriptions
) {
    public WordDescriptionSnapshot {
        descriptions = List.copyOf(descriptions);
    }
}

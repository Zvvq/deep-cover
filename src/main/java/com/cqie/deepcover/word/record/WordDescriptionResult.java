package com.cqie.deepcover.word.record;

/**
 * 提交描述后的结果。
 */
public record WordDescriptionResult(
        String roomCode,
        int roundNumber,
        WordDescriptionEntry description,
        WordDescriptionSnapshot snapshot,
        boolean votingStarted
) {
}

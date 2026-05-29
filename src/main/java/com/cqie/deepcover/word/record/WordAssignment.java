package com.cqie.deepcover.word.record;

/**
 * 单个玩家在某一轮拿到的词。
 *
 * <p>这里不记录玩家类型，避免后续真人卧底、多个 AI 等玩法变化时重复改存储结构。</p>
 */
public record WordAssignment(
        String roomCode,
        int roundNumber,
        String playerId,
        String word
) {
}

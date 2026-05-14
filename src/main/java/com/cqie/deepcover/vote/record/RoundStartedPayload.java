package com.cqie.deepcover.vote.record;

/**
 * 一轮投票结束且游戏未结束时，广播下一轮聊天开始。
 */
public record RoundStartedPayload(
        String roomCode,
        int roundNumber
) {
}

package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.topic.record.TopicSnapshot;

/**
 * 一轮投票结束且游戏未结束时，广播下一轮聊天开始。
 */
public record RoundStartedPayload(
        String roomCode,
        int roundNumber,
        TopicSnapshot topic
) {
}

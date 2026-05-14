package com.cqie.deepcover.vote.record;

import java.time.Instant;

/**
 * 一张已经提交的票。
 *
 * @param roomCode 房间号
 * @param roundNumber 轮次，从 1 开始
 * @param voterPlayerId 投票人
 * @param targetPlayerId 被投票人
 * @param createdAt 投票时间
 */
public record Vote(
        String roomCode,
        int roundNumber,
        String voterPlayerId,
        String targetPlayerId,
        Instant createdAt
) {
}

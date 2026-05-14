package com.cqie.deepcover.vote.record;

import java.time.Instant;

/**
 * 一个房间的一轮投票会话，用来记录当前处于第几轮。
 */
public record VoteSession(
        String roomCode,
        int roundNumber,
        Instant startedAt,
        boolean settled
) {
    public VoteSession settle() {
        return new VoteSession(roomCode, roundNumber, startedAt, true);
    }
}

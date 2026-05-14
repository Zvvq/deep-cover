package com.cqie.deepcover.vote.record;

/**
 * 广播给前端的投票进度。这里只广播进度，不公开每个人投给谁。
 */
public record VoteUpdatedPayload(
        String roomCode,
        int roundNumber,
        int submittedVoteCount,
        int requiredVoteCount
) {
}

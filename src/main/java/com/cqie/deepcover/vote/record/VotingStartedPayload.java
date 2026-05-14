package com.cqie.deepcover.vote.record;

import java.util.List;

/**
 * 广播给前端的投票开始事件内容。
 */
public record VotingStartedPayload(
        String roomCode,
        int roundNumber,
        List<String> candidatePlayerIds
) {
}

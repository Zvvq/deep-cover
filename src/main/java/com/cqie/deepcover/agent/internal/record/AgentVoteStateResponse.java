package com.cqie.deepcover.agent.internal.record;

import java.util.List;

/**
 * Agent 查询投票状态的响应。
 */
public record AgentVoteStateResponse(
        String roomCode,
        int roundNumber,
        int submittedVoteCount,
        int requiredVoteCount,
        List<String> candidatePlayerIds
) {
}

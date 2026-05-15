package com.cqie.deepcover.agent.event;

import java.util.List;

/**
 * 投票开始事件内容。
 */
public record AgentVotingStartedPayload(
        String roomCode,
        int roundNumber,
        List<String> candidatePlayerIds
) {
}

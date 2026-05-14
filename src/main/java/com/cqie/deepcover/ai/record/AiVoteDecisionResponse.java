package com.cqie.deepcover.ai.record;

/**
 * Python 投票决策接口返回值。
 *
 * @param targetPlayerId AI 想投给的玩家
 * @param reason 调试原因；MVP 不广播给玩家
 */
public record AiVoteDecisionResponse(
        String targetPlayerId,
        String reason
) {
}

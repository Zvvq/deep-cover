package com.cqie.deepcover.agent.event;

import com.cqie.deepcover.vote.enums.GameWinner;

/**
 * 游戏结束事件内容。
 */
public record AgentGameEndedPayload(
        GameWinner winner,
        String reason
) {
}

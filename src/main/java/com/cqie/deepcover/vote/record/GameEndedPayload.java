package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.vote.enums.GameWinner;

/**
 * 广播给前端的游戏结束结果。
 */
public record GameEndedPayload(
        String roomCode,
        GameWinner winner,
        String reason
) {
}

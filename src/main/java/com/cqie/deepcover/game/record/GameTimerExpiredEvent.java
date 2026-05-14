package com.cqie.deepcover.game.record;

import com.cqie.deepcover.game.enums.GamePhase;

import java.time.Instant;

/**
 * Spring 内部事件：计时器到期后通知玩法模块推进状态。
 */
public record GameTimerExpiredEvent(
        String roomCode,
        GamePhase phase,
        Instant expiredAt
) {
}

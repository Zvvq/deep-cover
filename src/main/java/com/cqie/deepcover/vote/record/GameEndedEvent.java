package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.vote.enums.GameWinner;

/**
 * Spring 内部事件：游戏结束。
 */
public record GameEndedEvent(
        String roomCode,
        GameWinner winner,
        String reason
) {
}

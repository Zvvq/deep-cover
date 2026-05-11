package com.cqie.deepcover.game.record;

import com.cqie.deepcover.game.enums.GamePhase;

import java.time.Instant;

/**
 * 计时器到期事件内容。
 *
 * @param roomCode 房间号
 * @param phase 到期的游戏阶段
 * @param expiredAt 后端确认到期的时间
 */
public record TimerExpiredPayload(String roomCode, GamePhase phase, Instant expiredAt) {
}

package com.cqie.deepcover.game.record;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.enums.TimerStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * 返回给前端展示的计时器快照。
 *
 * <p>`remainingSeconds` 每次查询时根据当前时间动态计算，前端拿到 `endsAt` 后可以自己每秒刷新 UI。</p>
 *
 * @param roomCode 房间号
 * @param phase 游戏阶段
 * @param status 计时器状态
 * @param durationSeconds 总时长，单位秒
 * @param startedAt 开始时间
 * @param endsAt 结束时间
 * @param remainingSeconds 剩余秒数
 */
public record GameTimerSnapshot(
        String roomCode,
        GamePhase phase,
        TimerStatus status,
        long durationSeconds,
        Instant startedAt,
        Instant endsAt,
        long remainingSeconds
) {
    public static GameTimerSnapshot from(GameTimer timer, Instant now) {
        long remainingSeconds = Math.max(0, Duration.between(now, timer.endsAt()).toSeconds());
        return new GameTimerSnapshot(
                timer.roomCode(),
                timer.phase(),
                timer.status(),
                timer.durationSeconds(),
                timer.startedAt(),
                timer.endsAt(),
                remainingSeconds
        );
    }
}

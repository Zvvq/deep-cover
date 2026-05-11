package com.cqie.deepcover.game.record;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.enums.TimerStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * game 模块内部保存的计时器记录。
 *
 * <p>这里使用不可变 record；状态变化时创建新的 `GameTimer`，再保存回仓库。</p>
 *
 * @param roomCode 房间号
 * @param phase 当前计时器对应的游戏阶段
 * @param status 计时器状态
 * @param durationSeconds 总时长，单位秒
 * @param startedAt 开始时间
 * @param endsAt 结束时间
 */
public record GameTimer(
        String roomCode,
        GamePhase phase,
        TimerStatus status,
        long durationSeconds,
        Instant startedAt,
        Instant endsAt
) {
    public static GameTimer start(String roomCode, GamePhase phase, Duration duration, Instant startedAt) {
        return new GameTimer(
                roomCode,
                phase,
                TimerStatus.RUNNING,
                duration.getSeconds(),
                startedAt,
                startedAt.plus(duration)
        );
    }

    public boolean dueAt(Instant now) {
        return status == TimerStatus.RUNNING && !now.isBefore(endsAt);
    }

    public GameTimer expire() {
        return new GameTimer(roomCode, phase, TimerStatus.EXPIRED, durationSeconds, startedAt, endsAt);
    }
}

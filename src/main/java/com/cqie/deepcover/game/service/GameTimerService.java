package com.cqie.deepcover.game.service;

import com.cqie.deepcover.game.enums.GameEventType;
import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.interfaces.GameTimerEventPublisher;
import com.cqie.deepcover.game.interfaces.GameTimerRepository;
import com.cqie.deepcover.game.record.GameRoomEvent;
import com.cqie.deepcover.game.record.GameTimer;
import com.cqie.deepcover.game.record.GameTimerExpiredEvent;
import com.cqie.deepcover.game.record.GameTimerSnapshot;
import com.cqie.deepcover.game.record.TimerExpiredPayload;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * game timer 的业务服务。
 *
 * <p>它只管理计时器本身：启动、查询、扫描到期、发布到期事件。后续是否进入投票阶段，
 * 会交给投票或 game 状态流转模块处理。</p>
 */
@Service
public class GameTimerService {
    private final GameTimerRepository gameTimerRepository;
    private final GameTimerEventPublisher gameTimerEventPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;

    public GameTimerService(
            GameTimerRepository gameTimerRepository,
            GameTimerEventPublisher gameTimerEventPublisher,
            Clock clock
    ) {
        this(gameTimerRepository, gameTimerEventPublisher, clock, event -> {
        });
    }

    @Autowired
    public GameTimerService(
            GameTimerRepository gameTimerRepository,
            GameTimerEventPublisher gameTimerEventPublisher,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.gameTimerRepository = gameTimerRepository;
        this.gameTimerEventPublisher = gameTimerEventPublisher;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 启动一个房间阶段计时器。
     *
     * @param roomCode 房间号
     * @param phase 游戏阶段
     * @param duration 计时时长
     * @return 计时器快照
     */
    public synchronized GameTimerSnapshot startTimer(String roomCode, GamePhase phase, Duration duration) {
        Instant now = clock.instant();
        GameTimer timer = GameTimer.start(roomCode, phase, duration, now);
        gameTimerRepository.save(timer);
        return GameTimerSnapshot.from(timer, now);
    }

    /**
     * 查询当前房间计时器。
     *
     * @param roomCode 房间号
     * @return 计时器快照
     */
    public GameTimerSnapshot snapshot(String roomCode) {
        GameTimer timer = findTimer(roomCode);
        return GameTimerSnapshot.from(timer, clock.instant());
    }

    /**
     * 扫描并过期所有已经到点的运行中计时器。
     * @return 本次过期的计时器数量
     */
    public synchronized int expireDueTimers() {
        Instant now = clock.instant();
        int expiredCount = 0;
        for (GameTimer timer : gameTimerRepository.findRunningTimers()) {
            if (timer.dueAt(now)) {
                GameTimer expiredTimer = timer.expire();
                gameTimerRepository.save(expiredTimer);
                publishExpiredEvent(expiredTimer, now);
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private GameTimer findTimer(String roomCode) {
        return gameTimerRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RoomException(RoomErrorCode.TIMER_NOT_FOUND, "Timer not found."));
    }

    private void publishExpiredEvent(GameTimer timer, Instant expiredAt) {
        TimerExpiredPayload payload = new TimerExpiredPayload(timer.roomCode(), timer.phase(), expiredAt);
        gameTimerEventPublisher.publish(
                timer.roomCode(),
                new GameRoomEvent(GameEventType.TIMER_EXPIRED, payload)
        );
        applicationEventPublisher.publishEvent(new GameTimerExpiredEvent(timer.roomCode(), timer.phase(), expiredAt));
    }
}

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
import com.cqie.deepcover.redis.lock.NoopRoomLockExecutor;
import com.cqie.deepcover.redis.lock.RoomLockExecutor;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * game timer 的业务服务。
 *
 * <p>它只管理计时器本身：启动、查询、扫描到期、发布到期事件。后续是否进入投票阶段，
 * 会交给投票或 game 状态流转模块处理。</p>
 */
@Service
public class GameTimerService {
    private static final Logger log = LoggerFactory.getLogger(GameTimerService.class);

    private final GameTimerRepository gameTimerRepository;
    private final GameTimerEventPublisher gameTimerEventPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RoomLockExecutor roomLockExecutor;

    public GameTimerService(
            GameTimerRepository gameTimerRepository,
            GameTimerEventPublisher gameTimerEventPublisher,
            Clock clock
    ) {
        this(gameTimerRepository, gameTimerEventPublisher, clock, event -> {
        }, new NoopRoomLockExecutor());
    }

    public GameTimerService(
            GameTimerRepository gameTimerRepository,
            GameTimerEventPublisher gameTimerEventPublisher,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this(gameTimerRepository, gameTimerEventPublisher, clock, applicationEventPublisher, new NoopRoomLockExecutor());
    }

    @Autowired
    public GameTimerService(
            GameTimerRepository gameTimerRepository,
            GameTimerEventPublisher gameTimerEventPublisher,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher,
            RoomLockExecutor roomLockExecutor
    ) {
        this.gameTimerRepository = gameTimerRepository;
        this.gameTimerEventPublisher = gameTimerEventPublisher;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.roomLockExecutor = roomLockExecutor;
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
        log.info("启动房间计时器，roomCode={}, phase={}, durationSeconds={}, startedAt={}, endsAt={}",
                roomCode, phase, duration.toSeconds(), timer.startedAt(), timer.endsAt());
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
            if (timer.dueAt(now) && roomLockExecutor.execute(timer.roomCode(), () -> expireTimerIfDue(timer.roomCode(), now))) {
                expiredCount++;
            }
        }
        if (expiredCount > 0) {
            log.info("计时器扫描完成，本次处理到期计时器数量={}", expiredCount);
        }
        return expiredCount;
    }

    private GameTimer findTimer(String roomCode) {
        return gameTimerRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RoomException(RoomErrorCode.TIMER_NOT_FOUND, "Timer not found."));
    }

    private boolean expireTimerIfDue(String roomCode, Instant now) {
        Optional<GameTimer> currentTimer = gameTimerRepository.findByRoomCode(roomCode);
        if (currentTimer.isEmpty() || !currentTimer.get().dueAt(now)) {
            return false;
        }

        GameTimer expiredTimer = currentTimer.get().expire();
        gameTimerRepository.save(expiredTimer);
        publishExpiredEvent(expiredTimer, now);
        return true;
    }

    private void publishExpiredEvent(GameTimer timer, Instant expiredAt) {
        TimerExpiredPayload payload = new TimerExpiredPayload(timer.roomCode(), timer.phase(), expiredAt);
        gameTimerEventPublisher.publish(
                timer.roomCode(),
                new GameRoomEvent(GameEventType.TIMER_EXPIRED, payload)
        );
        applicationEventPublisher.publishEvent(new GameTimerExpiredEvent(timer.roomCode(), timer.phase(), expiredAt));
        log.info("房间计时器到期，roomCode={}, phase={}, expiredAt={}", timer.roomCode(), timer.phase(), expiredAt);
    }
}

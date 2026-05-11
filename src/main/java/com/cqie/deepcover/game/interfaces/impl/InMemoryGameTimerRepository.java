package com.cqie.deepcover.game.interfaces.impl;

import com.cqie.deepcover.game.enums.TimerStatus;
import com.cqie.deepcover.game.interfaces.GameTimerRepository;
import com.cqie.deepcover.game.record.GameTimer;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版计时器仓库，临时代替 Redis 或数据库。
 */
@Repository
public class InMemoryGameTimerRepository implements GameTimerRepository {
    private final Map<String, GameTimer> timers = new ConcurrentHashMap<>();

    @Override
    public GameTimer save(GameTimer timer) {
        timers.put(timer.roomCode(), timer);
        return timer;
    }

    @Override
    public Optional<GameTimer> findByRoomCode(String roomCode) {
        return Optional.ofNullable(timers.get(roomCode));
    }

    @Override
    public List<GameTimer> findRunningTimers() {
        return timers.values().stream()
                .filter(timer -> timer.status() == TimerStatus.RUNNING)
                .toList();
    }
}

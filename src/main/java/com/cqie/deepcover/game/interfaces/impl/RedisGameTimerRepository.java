package com.cqie.deepcover.game.interfaces.impl;

import com.cqie.deepcover.game.enums.TimerStatus;
import com.cqie.deepcover.game.interfaces.GameTimerRepository;
import com.cqie.deepcover.game.record.GameTimer;
import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "deep-cover.game-timer.repository", havingValue = "redis")
public class RedisGameTimerRepository implements GameTimerRepository {
    private static final String TIMER_KEY_PREFIX = "dc:timer:";
    private static final String RUNNING_TIMERS_KEY = "dc:timers:running";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;

    public RedisGameTimerRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public GameTimer save(GameTimer timer) {
        String key = timerKey(timer.roomCode());
        redisTemplate.opsForValue().set(key, jsonHelper.write(timer));
        if (timer.status() == TimerStatus.RUNNING) {
            redisTemplate.opsForZSet().add(RUNNING_TIMERS_KEY, timer.roomCode(), timer.endsAt().toEpochMilli());
        } else {
            redisTemplate.opsForZSet().remove(RUNNING_TIMERS_KEY, timer.roomCode());
        }
        ttlService.registerActiveKey(timer.roomCode(), key);
        redisTemplate.expire(RUNNING_TIMERS_KEY, Duration.ofDays(1));
        return timer;
    }

    @Override
    public Optional<GameTimer> findByRoomCode(String roomCode) {
        String json = redisTemplate.opsForValue().get(timerKey(roomCode));
        if (json == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(jsonHelper.read(json, GameTimer.class, timerKey(roomCode)));
    }

    @Override
    public List<GameTimer> findRunningTimers() {
        var roomCodes = redisTemplate.opsForZSet().range(RUNNING_TIMERS_KEY, 0, -1);
        if (roomCodes == null || roomCodes.isEmpty()) {
            return List.of();
        }

        List<GameTimer> timers = new ArrayList<>();
        for (String roomCode : roomCodes) {
            Optional<GameTimer> timer = findByRoomCode(roomCode);
            if (timer.isPresent() && timer.get().status() == TimerStatus.RUNNING) {
                timers.add(timer.get());
            } else {
                redisTemplate.opsForZSet().remove(RUNNING_TIMERS_KEY, roomCode);
            }
        }
        return timers;
    }

    private String timerKey(String roomCode) {
        return TIMER_KEY_PREFIX + roomCode;
    }
}

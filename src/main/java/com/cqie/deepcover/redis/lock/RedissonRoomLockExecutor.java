package com.cqie.deepcover.redis.lock;

import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(name = "deep-cover.lock.provider", havingValue = "redisson")
public class RedissonRoomLockExecutor implements RoomLockExecutor {
    private static final String LOCK_KEY_PREFIX = "dc:lock:room:";

    private final RedissonClient redissonClient;
    private final Duration waitTime;

    public RedissonRoomLockExecutor(
            RedissonClient redissonClient,
            @Value("${deep-cover.redis.lock-wait-time:PT2S}") Duration waitTime
    ) {
        this.redissonClient = redissonClient;
        this.waitTime = waitTime;
    }

    @Override
    public <T> T execute(String roomCode, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + roomCode);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RoomException(RoomErrorCode.ROOM_BUSY, "Room is busy. Please retry.");
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RoomException(RoomErrorCode.ROOM_BUSY, "Interrupted while waiting for room lock.");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

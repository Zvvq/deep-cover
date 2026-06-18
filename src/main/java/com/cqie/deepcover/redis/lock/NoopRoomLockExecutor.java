package com.cqie.deepcover.redis.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "deep-cover.lock.provider", havingValue = "none", matchIfMissing = true)
public class NoopRoomLockExecutor implements RoomLockExecutor {
    @Override
    public <T> T execute(String roomCode, Supplier<T> action) {
        return action.get();
    }
}

package com.cqie.deepcover.redis.lock;

import java.util.function.Supplier;

public interface RoomLockExecutor {
    <T> T execute(String roomCode, Supplier<T> action);

    default void execute(String roomCode, Runnable action) {
        execute(roomCode, () -> {
            action.run();
            return null;
        });
    }
}

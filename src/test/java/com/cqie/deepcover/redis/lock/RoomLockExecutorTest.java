package com.cqie.deepcover.redis.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomLockExecutorTest {

    @Test
    void noopExecutorRunsRoomScopedSupplierAndReturnsResult() {
        RoomLockExecutor executor = new NoopRoomLockExecutor();

        String result = executor.execute("ABC123", () -> "locked-result");

        assertThat(result).isEqualTo("locked-result");
    }

    @Test
    void noopExecutorRunsRoomScopedRunnable() {
        RoomLockExecutor executor = new NoopRoomLockExecutor();
        AtomicBoolean called = new AtomicBoolean(false);

        executor.execute("ABC123", () -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void noopExecutorPropagatesFailures() {
        RoomLockExecutor executor = new NoopRoomLockExecutor();

        assertThatThrownBy(() -> executor.execute("ABC123", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}

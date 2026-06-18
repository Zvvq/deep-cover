package com.cqie.deepcover.redis.lock;

import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonRoomLockExecutorTest {

    @Test
    void executesActionUnderRedissonRoomLockAndUnlocksAfterSuccess() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("dc:lock:room:ABC123")).thenReturn(lock);
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonRoomLockExecutor executor = new RedissonRoomLockExecutor(redissonClient, Duration.ofSeconds(2));

        String result = executor.execute("ABC123", () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(lock).unlock();
    }

    @Test
    void throwsRoomBusyWhenRedissonLockCannotBeAcquired() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("dc:lock:room:ABC123")).thenReturn(lock);
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(false);
        RedissonRoomLockExecutor executor = new RedissonRoomLockExecutor(redissonClient, Duration.ofSeconds(2));

        assertThatThrownBy(() -> executor.execute("ABC123", () -> "unreachable"))
                .isInstanceOf(RoomException.class)
                .extracting("errorCode")
                .isEqualTo(RoomErrorCode.ROOM_BUSY);
    }
}

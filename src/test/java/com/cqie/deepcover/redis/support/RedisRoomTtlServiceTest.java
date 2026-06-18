package com.cqie.deepcover.redis.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRoomTtlServiceTest {

    @Test
    void registersRoomDataKeyAndAppliesActiveTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mockSetOperations(redisTemplate);
        ZSetOperations<String, String> zSetOperations = mockZSetOperations(redisTemplate);
        Clock clock = Clock.fixed(Instant.parse("2026-06-18T03:00:00Z"), ZoneId.of("UTC"));
        RedisRoomTtlService service = new RedisRoomTtlService(
                redisTemplate,
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                clock
        );

        service.registerActiveKey("ABC123", "dc:chat:ABC123:messages");

        verify(setOperations).add("dc:room:ABC123:keys", "dc:chat:ABC123:messages");
        verify(redisTemplate).expire("dc:chat:ABC123:messages", Duration.ofMinutes(30));
        verify(redisTemplate).expire("dc:room:ABC123:keys", Duration.ofMinutes(30));
        verify(zSetOperations).add("dc:room:codes", "ABC123", 1781753400000.0);
    }

    @Test
    void deletesRegisteredRoomDataAndRoomIndexes() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mockSetOperations(redisTemplate);
        ZSetOperations<String, String> zSetOperations = mockZSetOperations(redisTemplate);
        when(setOperations.members("dc:room:ABC123:keys")).thenReturn(Set.of(
                "dc:chat:ABC123:messages",
                "dc:timer:ABC123"
        ));
        RedisRoomTtlService service = new RedisRoomTtlService(
                redisTemplate,
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                Clock.fixed(Instant.parse("2026-06-18T03:00:00Z"), ZoneId.of("UTC"))
        );

        service.deleteRoomData("ABC123");

        verify(redisTemplate).delete(argThat((Collection<String> keys) ->
                keys.contains("dc:chat:ABC123:messages")
                        && keys.contains("dc:timer:ABC123")
                        && keys.contains("dc:room:ABC123")
                        && keys.contains("dc:room:ABC123:keys")
                        && keys.contains("dc:room-code:ABC123")
        ));
        verify(zSetOperations).remove("dc:room:codes", "ABC123");
    }

    @Test
    void roomDataKeysIncludeRegisteredKeysAndCoreRoomKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mockSetOperations(redisTemplate);
        mockZSetOperations(redisTemplate);
        when(setOperations.members("dc:room:ABC123:keys")).thenReturn(Set.of("dc:timer:ABC123"));
        RedisRoomTtlService service = new RedisRoomTtlService(
                redisTemplate,
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                Clock.fixed(Instant.parse("2026-06-18T03:00:00Z"), ZoneId.of("UTC"))
        );

        Set<String> keys = service.roomDataKeys("ABC123");

        assertThat(keys).containsExactlyInAnyOrder(
                "dc:timer:ABC123",
                "dc:room:ABC123",
                "dc:room:ABC123:keys",
                "dc:room-code:ABC123"
        );
    }

    @SuppressWarnings("unchecked")
    private SetOperations<String, String> mockSetOperations(StringRedisTemplate redisTemplate) {
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        return setOperations;
    }

    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> mockZSetOperations(StringRedisTemplate redisTemplate) {
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        return zSetOperations;
    }
}

package com.cqie.deepcover.redis.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class RedisRoomTtlService {
    public static final String ROOM_CODES_KEY = "dc:room:codes";
    public static final String ROOM_KEY_PREFIX = "dc:room:";
    public static final String ROOM_CODE_KEY_PREFIX = "dc:room-code:";

    private final StringRedisTemplate redisTemplate;
    private final Duration activeRoomTtl;
    private final Duration endedRoomTtl;
    private final Clock clock;

    public RedisRoomTtlService(
            StringRedisTemplate redisTemplate,
            @Value("${deep-cover.redis.room-ttl:PT2H}") Duration activeRoomTtl,
            @Value("${deep-cover.redis.ended-room-ttl:PT10M}") Duration endedRoomTtl,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.activeRoomTtl = activeRoomTtl;
        this.endedRoomTtl = endedRoomTtl;
        this.clock = clock;
    }

    public void registerActiveKey(String roomCode, String key) {
        registerKey(roomCode, key, activeRoomTtl);
    }

    public void registerEndedKey(String roomCode, String key) {
        registerKey(roomCode, key, endedRoomTtl);
    }

    public boolean reserveRoomCode(String roomCode) {
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(roomCodeReservationKey(roomCode), "1", activeRoomTtl);
        if (Boolean.TRUE.equals(reserved)) {
            updateRoomCodeIndex(roomCode, activeRoomTtl);
            return true;
        }
        return false;
    }

    public void touchRoom(String roomCode) {
        expireRoomData(roomCode, activeRoomTtl);
    }

    public void expireEndedRoom(String roomCode) {
        expireRoomData(roomCode, endedRoomTtl);
    }

    public void expireRoomData(String roomCode, Duration ttl) {
        for (String key : roomDataKeys(roomCode)) {
            redisTemplate.expire(key, ttl);
        }
        updateRoomCodeIndex(roomCode, ttl);
    }

    public void deleteRoomData(String roomCode) {
        Set<String> keys = roomDataKeys(roomCode);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.opsForZSet().remove(ROOM_CODES_KEY, roomCode);
    }

    public Set<String> roomDataKeys(String roomCode) {
        Set<String> keys = new LinkedHashSet<>();
        Set<String> registeredKeys = redisTemplate.opsForSet().members(roomKeysKey(roomCode));
        if (registeredKeys != null) {
            keys.addAll(registeredKeys);
        }
        keys.add(roomKey(roomCode));
        keys.add(roomKeysKey(roomCode));
        keys.add(roomCodeReservationKey(roomCode));
        return keys;
    }

    public String roomKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode;
    }

    public String roomKeysKey(String roomCode) {
        return ROOM_KEY_PREFIX + roomCode + ":keys";
    }

    public String roomCodeReservationKey(String roomCode) {
        return ROOM_CODE_KEY_PREFIX + roomCode;
    }

    private void registerKey(String roomCode, String key, Duration ttl) {
        String roomKeysKey = roomKeysKey(roomCode);
        redisTemplate.opsForSet().add(roomKeysKey, key);
        redisTemplate.expire(key, ttl);
        redisTemplate.expire(roomKeysKey, ttl);
        redisTemplate.expire(roomCodeReservationKey(roomCode), ttl);
        updateRoomCodeIndex(roomCode, ttl);
    }

    private void updateRoomCodeIndex(String roomCode, Duration ttl) {
        double expiresAtMillis = clock.instant().plus(ttl).toEpochMilli();
        redisTemplate.opsForZSet().add(ROOM_CODES_KEY, roomCode, expiresAtMillis);
    }
}

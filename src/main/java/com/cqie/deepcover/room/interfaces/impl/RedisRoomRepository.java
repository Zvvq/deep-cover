package com.cqie.deepcover.room.interfaces.impl;

import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.interfaces.RoomRepository;
import com.cqie.deepcover.room.model.Room;
import com.cqie.deepcover.room.record.RoomDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "deep-cover.room.repository", havingValue = "redis", matchIfMissing = true)
public class RedisRoomRepository implements RoomRepository {
    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int ROOM_CODE_LENGTH = 6;

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;
    private final SecureRandom random = new SecureRandom();

    public RedisRoomRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public void save(Room room) {
        String key = ttlService.roomKey(room.roomCode());
        redisTemplate.opsForValue().set(key, jsonHelper.write(room.toDocument()));
        redisTemplate.opsForValue().set(ttlService.roomCodeReservationKey(room.roomCode()), "1");
        if (room.status() == RoomStatus.ENDED || room.status() == RoomStatus.DESTROYED) {
            ttlService.registerEndedKey(room.roomCode(), key);
            ttlService.expireEndedRoom(room.roomCode());
        } else {
            ttlService.registerActiveKey(room.roomCode(), key);
        }
    }

    @Override
    public Optional<Room> findByCode(String roomCode) {
        String json = redisTemplate.opsForValue().get(ttlService.roomKey(roomCode));
        if (json == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(deserialize(json, roomCode));
    }

    @Override
    public List<Room> findAll() {
        var codes = redisTemplate.opsForZSet().range(RedisRoomTtlService.ROOM_CODES_KEY, 0, -1);
        if (codes == null || codes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Room> rooms = new ArrayList<>(codes.size());
        for (String code : codes) {
            String json = redisTemplate.opsForValue().get(ttlService.roomKey(code));
            if (json != null) {
                rooms.add(deserialize(json, code));
            } else {
                redisTemplate.opsForZSet().remove(RedisRoomTtlService.ROOM_CODES_KEY, code);
            }
        }
        return rooms;
    }

    @Override
    public void deleteByCode(String roomCode) {
        ttlService.deleteRoomData(roomCode);
    }

    @Override
    public String nextRoomCode() {
        String roomCode;
        do {
            roomCode = randomRoomCode();
        } while (!ttlService.reserveRoomCode(roomCode));
        return roomCode;
    }

    private Room deserialize(String json, String roomCode) {
        return Room.reconstitute(jsonHelper.read(json, RoomDocument.class, roomCode));
    }

    private String randomRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_CHARS[random.nextInt(ROOM_CODE_CHARS.length)]);
        }
        return code.toString();
    }
}

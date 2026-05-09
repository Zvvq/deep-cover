package com.cqie.deepcover.room.interfaces.impl;

import com.cqie.deepcover.room.interfaces.RoomRepository;
import com.cqie.deepcover.room.model.Room;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRoomRepository implements RoomRepository {
    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int ROOM_CODE_LENGTH = 6;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public void save(Room room) {
        rooms.put(room.roomCode(), room);
    }

    @Override
    public Optional<Room> findByCode(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode));
    }

    @Override
    public void deleteByCode(String roomCode) {
        rooms.remove(roomCode);
    }

    @Override
    public String nextRoomCode() {
        String roomCode;
        do {
            roomCode = randomRoomCode();
            // 房间码是给玩家口头/聊天复制用的，所以保持短且可读。
        } while (rooms.containsKey(roomCode));
        return roomCode;
    }

    private String randomRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_CHARS[random.nextInt(ROOM_CODE_CHARS.length)]);
        }
        return code.toString();
    }
}

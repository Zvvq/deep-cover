package com.cqie.deepcover.room.interfaces;

import com.cqie.deepcover.room.model.Room;

import java.util.Optional;

/**
 * 房间仓库接口。
 *
 * <p>MVP 使用内存实现，后续如果要接数据库，只需要替换 impl 包里的实现类。</p>
 */
public interface RoomRepository {
    void save(Room room);

    Optional<Room> findByCode(String roomCode);

    void deleteByCode(String roomCode);

    String nextRoomCode();
}

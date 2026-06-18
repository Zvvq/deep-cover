package com.cqie.deepcover.room.record;

import com.cqie.deepcover.room.enums.GameMode;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.topic.record.TopicSnapshot;

import java.util.List;

/**
 * 用于 Redis JSON 序列化的房间数据传输对象。
 *
 * <p>Room 领域对象具有私有构造器和复杂生命周期方法，不适合直接序列化。
 * RoomDocument 作为纯数据载体存储到 Redis，读取时再通过 RoomService/Repository 恢复为 Room 对象。</p>
 */
public record RoomDocument(
        String roomCode,
        String hostPlayerId,
        GameMode gameMode,
        List<Player> players,
        RoomStatus status,
        TopicSnapshot topic
) {
}

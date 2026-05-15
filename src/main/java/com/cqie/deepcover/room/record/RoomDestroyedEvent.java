package com.cqie.deepcover.room.record;

/**
 * Spring 内部事件：房间被销毁。
 */
public record RoomDestroyedEvent(String roomCode, String reason) {
}

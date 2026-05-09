package com.cqie.deepcover.room.enums;

/**
 * room 模块对外暴露的稳定错误码。
 *
 * <p>Controller 会把这些错误码映射成 HTTP 状态码，前端也可以直接用
 * errorCode 做提示和状态处理。</p>
 */
public enum RoomErrorCode {
    ROOM_NOT_FOUND,
    ROOM_NOT_JOINABLE,
    ROOM_FULL,
    FORBIDDEN,
    NOT_ENOUGH_PLAYERS,
    PLAYER_NOT_FOUND
}

package com.cqie.deepcover.room.enums;

/**
 * 房间生命周期状态。
 *
 * <p>这里先放 room 模块需要的状态；后续 game/chat 模块会继续细化
 * CHATTING、VOTING、ENDED 等状态的流转规则。</p>
 */
public enum RoomStatus {
    WAITING,
    CHATTING,
    VOTING,
    ENDED,
    DESTROYED
}

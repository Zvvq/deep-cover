package com.cqie.deepcover.room.record;

/**
 * 房间开始事件。
 *
 * <p>room 模块只负责宣布“房间已经开始”，具体启动计时器、后续进入投票等动作交给其他模块监听处理。</p>
 *
 * @param roomCode 已开始游戏的房间号
 */
public record RoomStartedEvent(String roomCode) {
}

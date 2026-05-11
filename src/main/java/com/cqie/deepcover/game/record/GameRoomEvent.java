package com.cqie.deepcover.game.record;

import com.cqie.deepcover.game.enums.GameEventType;

/**
 * game 模块广播给房间订阅者的统一事件外壳。
 *
 * <p>JSON 结构和 chat 模块的 RoomEvent 保持一致：前端统一通过 type 判断事件类型。</p>
 *
 * @param type 事件类型
 * @param payload 事件内容
 */
public record GameRoomEvent(GameEventType type, Object payload) {
}

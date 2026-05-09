package com.cqie.deepcover.chat.record;

import com.cqie.deepcover.chat.enums.RoomEventType;

/**
 * WebSocket 广播事件的统一外壳。
 *
 * <p>前端先看 type 判断是什么事件，再按事件类型解析 payload。</p>
 *
 * @param type 事件类型
 * @param payload 事件内容
 */
public record RoomEvent(RoomEventType type, Object payload) {
}

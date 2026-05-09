package com.cqie.deepcover.chat.enums;

/**
 * 房间内通过 WebSocket 广播的事件类型。
 *
 * <p>现在只有聊天消息，后续投票开始、投票结果、AI 发言等事件也可以继续加在这里。</p>
 */
public enum RoomEventType {
    CHAT_MESSAGE
}

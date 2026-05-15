package com.cqie.deepcover.chat.record;

/**
 * Spring 内部事件：聊天消息已经保存成功。
 */
public record ChatMessageCreatedEvent(
        String roomCode,
        ChatMessageResponse message
) {
}

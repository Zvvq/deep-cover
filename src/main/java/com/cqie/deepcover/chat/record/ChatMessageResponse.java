package com.cqie.deepcover.chat.record;

import java.time.Instant;

/**
 * 广播给前端的一条聊天消息。
 *
 * <p>这里不返回 playerToken，只返回发送者 ID，避免把玩家凭证暴露给其他客户端。</p>
 *
 * @param id 消息 ID
 * @param roomCode 房间码
 * @param senderPlayerId 发送者玩家 ID
 * @param content 聊天内容
 * @param createdAt 创建时间
 */
public record ChatMessageResponse(
        String id,
        String roomCode,
        String senderPlayerId,
        String content,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.id(),
                message.roomCode(),
                message.senderPlayerId(),
                message.content(),
                message.createdAt()
        );
    }
}

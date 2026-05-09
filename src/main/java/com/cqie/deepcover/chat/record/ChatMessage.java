package com.cqie.deepcover.chat.record;

import java.time.Instant;

/**
 * 后端保存的一条聊天消息。
 *
 * <p>它属于 chat 模块内部的数据记录；返回给前端时会转换成 {@link ChatMessageResponse}。</p>
 *
 * @param id 消息 ID
 * @param roomCode 房间码
 * @param senderPlayerId 发送者玩家 ID
 * @param content 聊天内容
 * @param createdAt 创建时间
 */
public record ChatMessage(
        String id,
        String roomCode,
        String senderPlayerId,
        String content,
        Instant createdAt
) {
}

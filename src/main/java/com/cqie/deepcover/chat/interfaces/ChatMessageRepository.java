package com.cqie.deepcover.chat.interfaces;

import com.cqie.deepcover.chat.record.ChatMessage;

import java.util.List;

/**
 * 聊天消息仓库接口。
 *
 * <p>现在用内存实现，后续如果要落库，只需要新增数据库版实现并替换 Spring Bean。</p>
 */
public interface ChatMessageRepository {
    ChatMessage save(ChatMessage message);

    List<ChatMessage> findByRoomCode(String roomCode);
}

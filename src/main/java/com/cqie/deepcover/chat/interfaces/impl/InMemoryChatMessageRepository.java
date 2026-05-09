package com.cqie.deepcover.chat.interfaces.impl;

import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版聊天消息仓库，临时代替数据库。
 *
 * <p>key 是房间号，value 是这个房间的聊天消息列表。因为聊天消息可能被多个 WebSocket 线程同时写入，
 * 这里使用并发集合，避免普通 ArrayList 在并发写入时出现数据问题。</p>
 */
@Repository
public class InMemoryChatMessageRepository implements ChatMessageRepository {
    private final Map<String, CopyOnWriteArrayList<ChatMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public ChatMessage save(ChatMessage message) {
        messages.computeIfAbsent(message.roomCode(), roomCode -> new CopyOnWriteArrayList<>())
                .add(message);
        return message;
    }

    @Override
    public List<ChatMessage> findByRoomCode(String roomCode) {
        return List.copyOf(messages.getOrDefault(roomCode, new CopyOnWriteArrayList<>()));
    }
}

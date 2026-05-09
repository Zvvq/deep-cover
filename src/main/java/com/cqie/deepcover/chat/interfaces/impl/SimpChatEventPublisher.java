package com.cqie.deepcover.chat.interfaces.impl;

import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.record.RoomEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring WebSocket 的事件发布实现。
 *
 * <p>发送到 `/topic/rooms/{roomCode}/events` 后，所有订阅这个地址的客户端都会收到事件。</p>
 */
@Component
public class SimpChatEventPublisher implements ChatEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public SimpChatEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String roomCode, RoomEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/events", event);
    }
}

package com.cqie.deepcover.chat.interfaces.impl;

import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.chat.record.RoomEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring WebSocket 的事件发布实现。
 *
 * <p>发送到 `/topic/rooms/{roomCode}/events` 后，所有订阅这个地址的客户端都会收到事件。</p>
 */
@Component
public class SimpChatEventPublisher implements ChatEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpChatEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public SimpChatEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String roomCode, RoomEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/events", event);
        if (event.payload() instanceof ChatMessageResponse message) {
            log.info("推送聊天消息事件，roomCode={}, eventType={}, messageId={}, senderPlayerId={}, content={}",
                    roomCode, event.type(), message.id(), message.senderPlayerId(), message.content());
        } else {
            log.info("推送房间事件，roomCode={}, eventType={}", roomCode, event.type());
        }
    }
}

package com.cqie.deepcover.game.interfaces.impl;

import com.cqie.deepcover.game.interfaces.GameTimerEventPublisher;
import com.cqie.deepcover.game.record.GameRoomEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring WebSocket 的计时器事件发布实现。
 */
@Component
public class SimpGameTimerEventPublisher implements GameTimerEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpGameTimerEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public SimpGameTimerEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String roomCode, GameRoomEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/events", event);
        log.info("推送计时器事件，roomCode={}, eventType={}", roomCode, event.type());
    }
}

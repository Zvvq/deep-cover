package com.cqie.deepcover.game.interfaces.impl;

import com.cqie.deepcover.game.interfaces.GameTimerEventPublisher;
import com.cqie.deepcover.game.record.GameRoomEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring WebSocket 的计时器事件发布实现。
 */
@Component
public class SimpGameTimerEventPublisher implements GameTimerEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public SimpGameTimerEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String roomCode, GameRoomEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/events", event);
    }
}

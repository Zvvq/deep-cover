package com.cqie.deepcover.game.listener;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.service.GameTimerService;
import com.cqie.deepcover.room.record.RoomStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 监听房间开始事件，并自动启动聊天阶段计时器。
 */
@Component
public class RoomStartedTimerListener {
    private static final Duration CHATTING_DURATION = Duration.ofSeconds(300);

    private final GameTimerService gameTimerService;

    public RoomStartedTimerListener(GameTimerService gameTimerService) {
        this.gameTimerService = gameTimerService;
    }

    @EventListener
    public void onRoomStarted(RoomStartedEvent event) {
        gameTimerService.startTimer(event.roomCode(), GamePhase.CHATTING, CHATTING_DURATION);
    }
}

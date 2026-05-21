package com.cqie.deepcover.game.listener;

import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.service.GameTimerService;
import com.cqie.deepcover.room.record.RoomStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 监听房间开始事件，并自动启动聊天阶段计时器。
 */
@Component
public class RoomStartedTimerListener {
    private static final Logger log = LoggerFactory.getLogger(RoomStartedTimerListener.class);
    private static final Duration CHATTING_DURATION = Duration.ofSeconds(300);

    private final GameTimerService gameTimerService;

    public RoomStartedTimerListener(GameTimerService gameTimerService) {
        this.gameTimerService = gameTimerService;
    }

    @EventListener
    public void onRoomStarted(RoomStartedEvent event) {
        gameTimerService.startTimer(event.roomCode(), GamePhase.CHATTING, CHATTING_DURATION);
        log.info("监听到房间开始事件，已启动聊天计时器，roomCode={}, durationSeconds={}",
                event.roomCode(), CHATTING_DURATION.toSeconds());
    }
}

package com.cqie.deepcover.game.interfaces;

import com.cqie.deepcover.game.record.GameRoomEvent;

/**
 * 计时器事件发布接口。
 */
public interface GameTimerEventPublisher {
    void publish(String roomCode, GameRoomEvent event);
}

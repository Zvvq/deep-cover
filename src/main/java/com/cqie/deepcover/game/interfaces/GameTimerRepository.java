package com.cqie.deepcover.game.interfaces;

import com.cqie.deepcover.game.record.GameTimer;

import java.util.List;
import java.util.Optional;

/**
 * 游戏计时器仓库接口。
 *
 * <p>当前实现用内存 Map；后续替换 Redis 时，Service 不需要改调用方式。</p>
 */
public interface GameTimerRepository {
    GameTimer save(GameTimer timer);

    Optional<GameTimer> findByRoomCode(String roomCode);

    List<GameTimer> findRunningTimers();
}

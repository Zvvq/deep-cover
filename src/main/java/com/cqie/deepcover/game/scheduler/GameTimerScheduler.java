package com.cqie.deepcover.game.scheduler;

import com.cqie.deepcover.game.service.GameTimerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一扫描所有运行中的计时器。
 *
 * <p>这里不是一个房间一个线程，而是一个后台任务定期扫描 Map。后续换成 Redis 时，
 * 也可以把扫描逻辑改成读取 Redis 中的运行中计时器。</p>
 */
@Component
public class GameTimerScheduler {
    private final GameTimerService gameTimerService;

    public GameTimerScheduler(GameTimerService gameTimerService) {
        this.gameTimerService = gameTimerService;
    }

    @Scheduled(fixedRate = 1000)
    public void expireDueTimers() {
        gameTimerService.expireDueTimers();
    }
}

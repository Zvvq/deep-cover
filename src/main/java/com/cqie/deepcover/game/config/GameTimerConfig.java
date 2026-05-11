package com.cqie.deepcover.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * game timer 基础配置。
 */
@Configuration
@EnableScheduling
public class GameTimerConfig {

    @Bean
    public Clock gameClock() {
        return Clock.systemUTC();
    }
}

package com.cqie.deepcover.ai.scheduler;

import com.cqie.deepcover.ai.service.AiSpeechService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每隔一段时间给 AI 一次发言决策机会。
 */
@Component
@ConditionalOnProperty(prefix = "deep-cover.ai.decision", name = "enabled", havingValue = "true")
public class AiSpeechScheduler {
    private final AiSpeechService aiSpeechService;

    public AiSpeechScheduler(AiSpeechService aiSpeechService) {
        this.aiSpeechService = aiSpeechService;
    }

    @Scheduled(fixedDelayString = "${deep-cover.ai.speech-interval-ms:5000}")
    public void triggerAiSpeech() {
        aiSpeechService.triggerForChattingRooms();
    }
}

package com.cqie.deepcover.ai.record;

/**
 * Python 发言决策接口返回值。
 *
 * @param shouldSpeak 是否发言
 * @param message 发言内容；shouldSpeak=false 时可以为空
 */
public record AiSpeechDecisionResponse(
        boolean shouldSpeak,
        String message
) {
    public static AiSpeechDecisionResponse doNotSpeak() {
        return new AiSpeechDecisionResponse(false, null);
    }
}

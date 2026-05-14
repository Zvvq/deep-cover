package com.cqie.deepcover.ai.interfaces.impl;

import com.cqie.deepcover.ai.interfaces.AiDecisionClient;
import com.cqie.deepcover.ai.record.AiSpeechDecisionRequest;
import com.cqie.deepcover.ai.record.AiSpeechDecisionResponse;
import com.cqie.deepcover.ai.record.AiVoteDecisionRequest;
import com.cqie.deepcover.ai.record.AiVoteDecisionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通过 HTTP 调用 Python AI 决策服务。
 *
 * <p>默认 deep-cover.ai.enabled=false，所以当前没有 Python 服务时项目也能正常运行。
 * 后续接入 Python 后，只需要打开配置并保证接口路径一致。</p>
 */
@Component
public class HttpAiDecisionClient implements AiDecisionClient {
    private final boolean enabled;
    private final RestClient restClient;
    private final String speechPath;
    private final String votePath;

    public HttpAiDecisionClient(
            RestClient.Builder restClientBuilder,
            @Value("${deep-cover.ai.enabled:false}") boolean enabled,
            @Value("${deep-cover.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${deep-cover.ai.speech-path:/ai/speech/decide}") String speechPath,
            @Value("${deep-cover.ai.vote-path:/ai/vote/decide}") String votePath
    ) {
        this.enabled = enabled;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.speechPath = speechPath;
        this.votePath = votePath;
    }

    @Override
    public AiSpeechDecisionResponse decideSpeech(AiSpeechDecisionRequest request) {
        if (!enabled) {
            return AiSpeechDecisionResponse.doNotSpeak();
        }
        try {
            AiSpeechDecisionResponse response = restClient.post()
                    .uri(speechPath)
                    .body(request)
                    .retrieve()
                    .body(AiSpeechDecisionResponse.class);
            return response == null ? AiSpeechDecisionResponse.doNotSpeak() : response;
        } catch (RuntimeException exception) {
            return AiSpeechDecisionResponse.doNotSpeak();
        }
    }

    @Override
    public AiVoteDecisionResponse decideVote(AiVoteDecisionRequest request) {
        if (!enabled) {
            return new AiVoteDecisionResponse(null, "AI decision client is disabled.");
        }
        try {
            AiVoteDecisionResponse response = restClient.post()
                    .uri(votePath)
                    .body(request)
                    .retrieve()
                    .body(AiVoteDecisionResponse.class);
            return response == null ? new AiVoteDecisionResponse(null, "Empty AI vote response.") : response;
        } catch (RuntimeException exception) {
            return new AiVoteDecisionResponse(null, "AI vote request failed.");
        }
    }
}

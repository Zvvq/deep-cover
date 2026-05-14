package com.cqie.deepcover.ai.interfaces;

import com.cqie.deepcover.ai.record.AiSpeechDecisionRequest;
import com.cqie.deepcover.ai.record.AiSpeechDecisionResponse;
import com.cqie.deepcover.ai.record.AiVoteDecisionRequest;
import com.cqie.deepcover.ai.record.AiVoteDecisionResponse;

/**
 * AI 决策服务客户端接口。
 *
 * <p>Java 只依赖这个接口，不关心 Python 内部是普通大模型提示词、LangChain Agent，
 * 还是临时规则实现。</p>
 */
public interface AiDecisionClient {
    AiSpeechDecisionResponse decideSpeech(AiSpeechDecisionRequest request);

    AiVoteDecisionResponse decideVote(AiVoteDecisionRequest request);
}

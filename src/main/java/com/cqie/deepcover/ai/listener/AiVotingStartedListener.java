package com.cqie.deepcover.ai.listener;

import com.cqie.deepcover.ai.service.AiVoteService;
import com.cqie.deepcover.vote.record.VotingStartedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 投票开始后自动让 AI 提交投票。
 */
@Component
@ConditionalOnProperty(prefix = "deep-cover.ai.decision", name = "enabled", havingValue = "true")
public class AiVotingStartedListener {
    private final AiVoteService aiVoteService;

    public AiVotingStartedListener(AiVoteService aiVoteService) {
        this.aiVoteService = aiVoteService;
    }

    @EventListener
    public void onVotingStarted(VotingStartedEvent event) {
        aiVoteService.castVotesForRoom(event.roomCode());
    }
}

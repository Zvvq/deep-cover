package com.cqie.deepcover.ai.service;

import com.cqie.deepcover.ai.interfaces.AiDecisionClient;
import com.cqie.deepcover.ai.record.AiChatMessageContext;
import com.cqie.deepcover.ai.record.AiPlayerContext;
import com.cqie.deepcover.ai.record.AiVoteDecisionRequest;
import com.cqie.deepcover.ai.record.AiVoteDecisionResponse;
import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.service.VoteService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AI 投票业务服务。
 *
 * <p>它只负责让 AI 产生投票决策，真正保存投票和结算仍然交给 VoteService。</p>
 */
@Service
public class AiVoteService {
    private final RoomService roomService;
    private final ChatMessageRepository chatMessageRepository;
    private final VoteService voteService;
    private final AiDecisionClient aiDecisionClient;
    private final Random random = new SecureRandom();

    public AiVoteService(
            RoomService roomService,
            ChatMessageRepository chatMessageRepository,
            VoteService voteService,
            AiDecisionClient aiDecisionClient
    ) {
        this.roomService = roomService;
        this.chatMessageRepository = chatMessageRepository;
        this.voteService = voteService;
        this.aiDecisionClient = aiDecisionClient;
    }

    /**
     * 投票阶段开始后，为房间内所有存活 AI 提交投票。
     */
    public void castVotesForRoom(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.status() != RoomStatus.VOTING) {
            return;
        }

        int roundNumber = voteService.currentRound(roomCode);
        if (roundNumber <= 0) {
            return;
        }

        for (PlayerSnapshot ai : aliveAiPlayers(room)) {
            if (voteService.hasVoted(roomCode, roundNumber, ai.id())) {
                continue;
            }
            castVoteForAi(roomCode, roundNumber, ai.id());
            if (roomService.snapshot(roomCode).status() != RoomStatus.VOTING) {
                return;
            }
        }
    }

    private void castVoteForAi(String roomCode, int roundNumber, String aiPlayerId) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        List<String> candidatePlayerIds = candidatePlayerIds(room, aiPlayerId);
        if (candidatePlayerIds.isEmpty()) {
            return;
        }

        String targetPlayerId = decideTarget(roomCode, roundNumber, aiPlayerId, room, candidatePlayerIds);
        try {
            voteService.castSystemVote(roomCode, aiPlayerId, targetPlayerId);
        } catch (RoomException ignored) {
            // AI 投票失败不应该打断房间流程，投票计时结束时仍会结算已有投票。
        }
    }

    private String decideTarget(
            String roomCode,
            int roundNumber,
            String aiPlayerId,
            RoomSnapshot room,
            List<String> candidatePlayerIds
    ) {
        try {
            AiVoteDecisionResponse response = aiDecisionClient.decideVote(new AiVoteDecisionRequest(
                    roomCode,
                    roundNumber,
                    aiPlayerId,
                    room.players().stream().map(this::toPlayerContext).toList(),
                    chatMessageRepository.findByRoomCode(roomCode).stream().map(this::toMessageContext).toList(),
                    candidatePlayerIds
            ));
            if (response != null && candidatePlayerIds.contains(response.targetPlayerId())) {
                return response.targetPlayerId();
            }
        } catch (RuntimeException ignored) {
            // 外部 AI 服务不可用时使用兜底目标。
        }
        return fallbackTarget(room, aiPlayerId);
    }

    private List<PlayerSnapshot> aliveAiPlayers(RoomSnapshot room) {
        return room.players().stream()
                .filter(player -> player.alive() && player.type() == PlayerType.AI)
                .toList();
    }

    private List<String> candidatePlayerIds(RoomSnapshot room, String aiPlayerId) {
        return room.players().stream()
                .filter(PlayerSnapshot::alive)
                .filter(player -> !player.id().equals(aiPlayerId))
                .map(PlayerSnapshot::id)
                .toList();
    }

    private String fallbackTarget(RoomSnapshot room, String aiPlayerId) {
        List<PlayerSnapshot> humanCandidates = room.players().stream()
                .filter(player -> player.alive() && player.type() == PlayerType.HUMAN)
                .toList();
        if (!humanCandidates.isEmpty()) {
            return humanCandidates.get(random.nextInt(humanCandidates.size())).id();
        }

        List<String> candidates = new ArrayList<>(candidatePlayerIds(room, aiPlayerId));
        return candidates.get(random.nextInt(candidates.size()));
    }

    private AiPlayerContext toPlayerContext(PlayerSnapshot player) {
        return new AiPlayerContext(player.id(), player.type(), player.alive());
    }

    private AiChatMessageContext toMessageContext(ChatMessage message) {
        return new AiChatMessageContext(message.senderPlayerId(), message.content(), message.createdAt());
    }
}

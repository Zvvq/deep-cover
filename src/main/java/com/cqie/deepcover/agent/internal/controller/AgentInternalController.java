package com.cqie.deepcover.agent.internal.controller;

import com.cqie.deepcover.agent.internal.record.AgentMessageCommand;
import com.cqie.deepcover.agent.internal.record.AgentRecentMessagesResponse;
import com.cqie.deepcover.agent.internal.record.AgentRoomStateResponse;
import com.cqie.deepcover.agent.internal.record.AgentVoteCommand;
import com.cqie.deepcover.agent.internal.record.AgentVoteStateResponse;
import com.cqie.deepcover.agent.internal.service.AgentInternalAuthService;
import com.cqie.deepcover.agent.internal.service.AgentInternalService;
import com.cqie.deepcover.chat.record.ChatMessageResponse;
import com.cqie.deepcover.vote.record.VoteResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Python Agent 调用 Java 的内部接口。
 */
@RestController
public class AgentInternalController {
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Agent-Secret";

    private final AgentInternalAuthService authService;
    private final AgentInternalService agentInternalService;

    public AgentInternalController(
            AgentInternalAuthService authService,
            AgentInternalService agentInternalService
    ) {
        this.authService = authService;
        this.agentInternalService = agentInternalService;
    }

    @GetMapping("/api/internal/agent/rooms/{roomCode}/state")
    public AgentRoomStateResponse roomState(
            @PathVariable String roomCode,
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret
    ) {
        authService.requireAuthorized(internalSecret);
        return agentInternalService.roomState(roomCode);
    }

    @GetMapping("/api/internal/agent/rooms/{roomCode}/messages")
    public AgentRecentMessagesResponse recentMessages(
            @PathVariable String roomCode,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret
    ) {
        authService.requireAuthorized(internalSecret);
        return agentInternalService.recentMessages(roomCode, limit);
    }

    @GetMapping("/api/internal/agent/rooms/{roomCode}/votes")
    public AgentVoteStateResponse voteState(
            @PathVariable String roomCode,
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret
    ) {
        authService.requireAuthorized(internalSecret);
        return agentInternalService.voteState(roomCode);
    }

    @PostMapping("/api/internal/agent/rooms/{roomCode}/messages")
    public ChatMessageResponse sendMessage(
            @PathVariable String roomCode,
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestBody AgentMessageCommand command
    ) {
        authService.requireAuthorized(internalSecret);
        return agentInternalService.sendMessage(roomCode, command);
    }

    @PostMapping("/api/internal/agent/rooms/{roomCode}/votes")
    public VoteResult castVote(
            @PathVariable String roomCode,
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestBody AgentVoteCommand command
    ) {
        authService.requireAuthorized(internalSecret);
        return agentInternalService.castVote(roomCode, command);
    }
}

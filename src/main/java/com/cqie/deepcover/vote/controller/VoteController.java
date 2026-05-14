package com.cqie.deepcover.vote.controller;

import com.cqie.deepcover.vote.record.VoteRequest;
import com.cqie.deepcover.vote.record.VoteResult;
import com.cqie.deepcover.vote.record.VoteSnapshot;
import com.cqie.deepcover.vote.service.VoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投票 HTTP 接口。实时广播仍然走房间 WebSocket 事件。
 */
@RestController
public class VoteController {
    private static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping("/api/rooms/{roomCode}/votes")
    public VoteResult castVote(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken,
            @RequestBody VoteRequest request
    ) {
        return voteService.castVote(roomCode, playerToken, request);
    }

    @GetMapping("/api/rooms/{roomCode}/votes")
    public VoteSnapshot snapshot(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        return voteService.snapshot(roomCode, playerToken);
    }
}

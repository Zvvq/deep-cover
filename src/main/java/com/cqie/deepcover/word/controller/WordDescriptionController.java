package com.cqie.deepcover.word.controller;

import com.cqie.deepcover.word.record.WordDescriptionRequest;
import com.cqie.deepcover.word.record.WordDescriptionResult;
import com.cqie.deepcover.word.record.WordDescriptionSnapshot;
import com.cqie.deepcover.word.service.WordDescriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关键词卧底描述阶段 HTTP 接口。
 */
@RestController
public class WordDescriptionController {
    private static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final WordDescriptionService wordDescriptionService;

    public WordDescriptionController(WordDescriptionService wordDescriptionService) {
        this.wordDescriptionService = wordDescriptionService;
    }

    @PostMapping("/api/rooms/{roomCode}/word/descriptions")
    public WordDescriptionResult submitDescription(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken,
            @RequestBody WordDescriptionRequest request
    ) {
        return wordDescriptionService.submitDescription(roomCode, playerToken, request);
    }

    @GetMapping("/api/rooms/{roomCode}/word/descriptions")
    public WordDescriptionSnapshot snapshot(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        return wordDescriptionService.snapshot(roomCode, playerToken);
    }
}

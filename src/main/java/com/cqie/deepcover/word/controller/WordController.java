package com.cqie.deepcover.word.controller;

import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.word.record.PlayerWordRequest;
import com.cqie.deepcover.word.record.PlayerWordResponse;
import com.cqie.deepcover.word.service.WordGameService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关键词卧底模式的玩家私有接口。
 *
 * <p>这里只提供“查自己的词”，不会把所有人的词暴露给前端。</p>
 */
@RestController
@RequestMapping("/api/rooms/{roomCode}/word")
public class WordController {
    private final RoomService roomService;
    private final WordGameService wordGameService;

    public WordController(RoomService roomService, WordGameService wordGameService) {
        this.roomService = roomService;
        this.wordGameService = wordGameService;
    }

    @PostMapping("/me")
    public PlayerWordResponse myWord(
            @PathVariable String roomCode,
            @RequestBody PlayerWordRequest request
    ) {
        String playerToken = request == null ? null : request.playerToken();
        Player player = roomService.requireWordParticipant(roomCode, playerToken);
        RoomSnapshot room = roomService.snapshot(roomCode);
        return wordGameService.findPlayerWord(room, player.id());
    }
}

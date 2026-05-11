package com.cqie.deepcover.game.controller;

import com.cqie.deepcover.game.record.GameTimerSnapshot;
import com.cqie.deepcover.game.service.GameTimerService;
import com.cqie.deepcover.room.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计时器查询接口。
 *
 * <p>前端通过这个接口拉取权威的 `endsAt` 和 `remainingSeconds`，再在本地每秒刷新展示。</p>
 */
@RestController
public class GameTimerController {
    private static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final GameTimerService gameTimerService;
    private final RoomService roomService;

    public GameTimerController(GameTimerService gameTimerService, RoomService roomService) {
        this.gameTimerService = gameTimerService;
        this.roomService = roomService;
    }

    /**
     * 查询当前房间计时器。
     *
     * @param roomCode 房间号
     * @param playerToken 当前玩家凭证
     * @return 计时器快照
     */
    @GetMapping("/api/rooms/{roomCode}/timer")
    public GameTimerSnapshot snapshot(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        roomService.requireChatParticipant(roomCode, playerToken);
        return gameTimerService.snapshot(roomCode);
    }
}

package com.cqie.deepcover.room.controller;

import com.cqie.deepcover.room.record.RoomDtos;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * room 模块的 HTTP 入口。
 *
 * <p>这里只负责协议映射：读路径参数、读 token header、返回 DTO。
 * 具体规则都放在 RoomService，方便后续 WebSocket 入口复用同一套逻辑。</p>
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * 创建房间，返回房间码和玩家 token。
     * @return 创建房间的响应
     */
    @PostMapping
    public ResponseEntity<RoomDtos.CreateRoomResponse> createRoom() {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RoomDtos.CreateRoomResponse.from(roomService.createRoom()));
    }

    /**
     * 加入房间，返回玩家 token 和当前房间状态。
     * @param roomCode 房间码
     * @return 加入房间的响应
     */
    @PostMapping("/{roomCode}/join")
    public RoomDtos.JoinRoomResponse joinRoom(@PathVariable String roomCode) {
        return RoomDtos.JoinRoomResponse.from(roomService.joinRoom(roomCode));
    }

    /**
     * 开始房间，返回当前房间状态。
     * @param roomCode 房间码
     * @param playerToken 玩家 token
     * @return 当前房间状态
     */
    @PostMapping("/{roomCode}/start")
    public RoomSnapshot startRoom(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        return roomService.startRoom(roomCode, playerToken);
    }

    /**
     * 离开房间，返回当前房间状态。
     * @param roomCode 房间码
     * @param playerToken 玩家 token
     * @return 当前房间状态
     */
    @PostMapping("/{roomCode}/leave")
    public RoomSnapshot leaveRoom(
            @PathVariable String roomCode,
            @RequestHeader(PLAYER_TOKEN_HEADER) String playerToken
    ) {
        return roomService.leaveRoom(roomCode, playerToken);
    }

    /**
     * 房间快照，返回当前房间状态。
     * @param roomCode 房间码
     * @return 当前房间状态
     */
    @GetMapping("/{roomCode}/snapshot")
    public RoomSnapshot snapshot(@PathVariable String roomCode) {
        return roomService.snapshot(roomCode);
    }
}

package com.cqie.deepcover.room.exception;

import com.cqie.deepcover.room.record.RoomDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 room 业务异常统一翻译成 HTTP 响应。
 *
 * <p>这样 Controller 可以保持干净，测试也能稳定校验 errorCode。</p>
 */
@RestControllerAdvice
public class RoomExceptionHandler {

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<RoomDtos.ErrorResponse> handleRoomException(RoomException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ROOM_NOT_JOINABLE, ROOM_NOT_CHATTING, ROOM_FULL, NOT_ENOUGH_PLAYERS,
                 PLAYER_NOT_FOUND, INVALID_CHAT_MESSAGE -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity
                .status(status)
                .body(new RoomDtos.ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }
}

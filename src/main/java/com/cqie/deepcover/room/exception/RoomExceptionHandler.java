package com.cqie.deepcover.room.exception;

import com.cqie.deepcover.room.record.RoomDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(RoomExceptionHandler.class);

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<RoomDtos.ErrorResponse> handleRoomException(RoomException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case ROOM_NOT_FOUND, TIMER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ROOM_NOT_JOINABLE, ROOM_NOT_CHATTING, ROOM_NOT_DESCRIBING, ROOM_NOT_VOTING,
                 ROOM_MODE_NOT_SUPPORTED, ROOM_FULL, NOT_ENOUGH_PLAYERS,
                 PLAYER_NOT_FOUND, PLAYER_NOT_ALIVE, INVALID_CHAT_MESSAGE, WORD_NOT_ASSIGNED,
                 INVALID_WORD_DESCRIPTION, WORD_DESCRIPTION_TURN_MISMATCH, INVALID_VOTE,
                 DUPLICATE_VOTE -> HttpStatus.BAD_REQUEST;
        };

        log.warn("业务请求处理失败，errorCode={}, httpStatus={}, message={}",
                exception.getErrorCode(), status.value(), exception.getMessage());
        return ResponseEntity
                .status(status)
                .body(new RoomDtos.ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }
}

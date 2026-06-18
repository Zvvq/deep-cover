package com.cqie.deepcover.room.service;

import com.cqie.deepcover.redis.lock.RoomLockExecutor;
import com.cqie.deepcover.room.interfaces.impl.InMemoryRoomRepository;
import com.cqie.deepcover.room.record.RoomCreateResult;
import com.cqie.deepcover.topic.interfaces.impl.InMemoryTopicRepository;
import com.cqie.deepcover.topic.service.TopicService;
import com.cqie.deepcover.word.interfaces.impl.InMemoryWordAssignmentRepository;
import com.cqie.deepcover.word.interfaces.impl.InMemoryWordPairRepository;
import com.cqie.deepcover.word.service.WordGameService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceLockTest {

    @Test
    void joinAndStartRunUnderRoomLock() {
        RecordingRoomLockExecutor lockExecutor = new RecordingRoomLockExecutor();
        RoomService service = new RoomService(
                new InMemoryRoomRepository(),
                event -> {
                },
                new TopicService(new InMemoryTopicRepository()),
                new WordGameService(new InMemoryWordPairRepository(), new InMemoryWordAssignmentRepository()),
                lockExecutor
        );
        RoomCreateResult created = service.createRoom();

        service.joinRoom(created.roomCode());
        service.startRoom(created.roomCode(), created.playerToken());

        assertThat(lockExecutor.roomCodes()).containsExactly(created.roomCode(), created.roomCode());
    }

    private static class RecordingRoomLockExecutor implements RoomLockExecutor {
        private final List<String> roomCodes = new ArrayList<>();

        @Override
        public <T> T execute(String roomCode, Supplier<T> action) {
            roomCodes.add(roomCode);
            return action.get();
        }

        private List<String> roomCodes() {
            return roomCodes;
        }
    }
}

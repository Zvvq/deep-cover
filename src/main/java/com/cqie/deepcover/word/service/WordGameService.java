package com.cqie.deepcover.word.service;

import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.word.interfaces.WordAssignmentRepository;
import com.cqie.deepcover.word.interfaces.WordPairRepository;
import com.cqie.deepcover.word.record.PlayerWordResponse;
import com.cqie.deepcover.word.record.WordAssignment;
import com.cqie.deepcover.word.record.WordPair;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 关键词卧底模式的词语分配服务。
 *
 * <p>它只负责抽词、分配和查询自己的词；描述顺序、投票淘汰、胜负结算后续放到 game/vote 模块。</p>
 */
@Service
public class WordGameService {
    private static final int INITIAL_ROUND_NUMBER = 1;

    private final WordPairRepository wordPairRepository;
    private final WordAssignmentRepository wordAssignmentRepository;
    private final Random random = new SecureRandom();

    public WordGameService(
            WordPairRepository wordPairRepository,
            WordAssignmentRepository wordAssignmentRepository
    ) {
        this.wordPairRepository = wordPairRepository;
        this.wordAssignmentRepository = wordAssignmentRepository;
    }

    /**
     * 开局第一轮分词。真人拿平民词，AI 拿卧底词。
     */
    public void assignInitialWords(RoomSnapshot room) {
        assignWords(room, INITIAL_ROUND_NUMBER);
    }

    /**
     * 给当前存活玩家分配本轮关键词。
     */
    public void assignWords(RoomSnapshot room, int roundNumber) {
        WordPair pair = randomWordPair();
        List<WordAssignment> assignments = room.players().stream()
                .filter(PlayerSnapshot::alive)
                .map(player -> new WordAssignment(
                        room.roomCode(),
                        roundNumber,
                        player.id(),
                        player.type() == PlayerType.AI ? pair.undercoverWord() : pair.civilianWord()
                ))
                .toList();

        wordAssignmentRepository.saveAll(room.roomCode(), roundNumber, assignments);
    }

    /**
     * 查询某个玩家自己的关键词，响应里附带 number/color 方便前端直接展示。
     */
    public PlayerWordResponse findPlayerWord(RoomSnapshot room, String playerId) {
        PlayerSnapshot player = room.players().stream()
                .filter(candidate -> candidate.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
        WordAssignment assignment = wordAssignmentRepository
                .findLatestByRoomCodeAndPlayerId(room.roomCode(), playerId)
                .orElseThrow(() -> new RoomException(RoomErrorCode.WORD_NOT_ASSIGNED, "Player word is not assigned."));

        return new PlayerWordResponse(player.id(), player.number(), player.color(), assignment.word());
    }

    public int currentRound(String roomCode) {
        return wordAssignmentRepository.findLatestRoundNumber(roomCode).orElse(0);
    }

    private WordPair randomWordPair() {
        List<WordPair> pairs = wordPairRepository.findAll();
        if (pairs.isEmpty()) {
            throw new IllegalStateException("Word pair pool cannot be empty.");
        }
        return pairs.get(random.nextInt(pairs.size()));
    }
}

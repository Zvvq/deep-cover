package com.cqie.deepcover.word.interfaces.impl;

import com.cqie.deepcover.word.interfaces.WordPairRepository;
import com.cqie.deepcover.word.record.WordPair;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内存关键词词库。
 *
 * <p>每组词需要相近但不相同，方便玩家描述时产生误导空间。</p>
 */
@Repository
public class InMemoryWordPairRepository implements WordPairRepository {
    private static final List<WordPair> WORD_PAIRS = List.of(
            new WordPair("word-pair-001", "奶茶", "酱油"),
            new WordPair("word-pair-002", "火锅", "洗发水"),
            new WordPair("word-pair-003", "电影院", "停车场"),
            new WordPair("word-pair-004", "雨伞", "筷子"),
            new WordPair("word-pair-005", "手机", "牙刷")
    );

    @Override
    public List<WordPair> findAll() {
        return WORD_PAIRS;
    }
}

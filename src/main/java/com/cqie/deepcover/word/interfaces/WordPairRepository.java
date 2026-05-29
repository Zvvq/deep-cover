package com.cqie.deepcover.word.interfaces;

import com.cqie.deepcover.word.record.WordPair;

import java.util.List;

/**
 * 关键词词库接口。
 *
 * <p>MVP 使用内存词库；后续可以换成数据库、配置文件或后台管理维护的词库。</p>
 */
public interface WordPairRepository {
    List<WordPair> findAll();
}

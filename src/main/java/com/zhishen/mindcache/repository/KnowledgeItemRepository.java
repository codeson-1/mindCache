package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * KnowledgeItem 数据访问层。
 * <p>继承 JpaRepository 自动获得 CRUD + 分页 + 排序能力。
 */
@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {

    /**
     * 按自动分类分页查询。
     *
     * @param autoCategory AI 自动分类（如 TECH/WORK/LIFE）
     * @param pageable     分页参数
     */
    Page<KnowledgeItem> findByAutoCategory(String autoCategory, Pageable pageable);
}

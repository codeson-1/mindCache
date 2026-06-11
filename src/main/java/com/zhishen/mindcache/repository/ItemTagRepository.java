package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.ItemTag;
import com.zhishen.mindcache.model.entity.ItemTag.ItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ItemTag 数据访问层。
 * <p>主键为联合主键 (item_id, tag_id)。
 */
@Repository
public interface ItemTagRepository extends JpaRepository<ItemTag, ItemTagId> {

    /**
     * 按知识条目 ID 查询所有关联标签。
     *
     * @param itemId 知识条目 UUID
     */
    List<ItemTag> findByItemId(UUID itemId);

    /**
     * 按知识条目 ID 删除所有关联标签。
     * 用于更新笔记时先删后建。
     *
     * @param itemId 知识条目 UUID
     */
    void deleteByItemId(UUID itemId);
}

package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tag 数据访问层。
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    /**
     * 按标签名精确查找（标签名全局唯一）。
     *
     * @param name 标签名称
     */
    Optional<Tag> findByName(String name);
}

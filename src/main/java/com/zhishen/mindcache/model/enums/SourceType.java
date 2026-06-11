package com.zhishen.mindcache.model.enums;

/**
 * 知识条目的数据来源。
 * <ul>
 *   <li>{@code MANUAL}   — 手动输入（文字）</li>
 *   <li>{@code VOICE}    — 语音录入</li>
 *   <li>{@code UPLOAD}   — 文件上传（图片）</li>
 *   <li>{@code WEB_CLIP} — 网页剪藏（v2）</li>
 * </ul>
 */
public enum SourceType {
    /** 手动输入 */
    MANUAL,
    /** 语音录入 */
    VOICE,
    /** 文件上传 */
    UPLOAD,
    /** 网页剪藏（v2） */
    WEB_CLIP
}

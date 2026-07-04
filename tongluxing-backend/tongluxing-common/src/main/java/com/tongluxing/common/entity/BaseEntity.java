package com.tongluxing.common.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 数据库实体基础字段。
 *
 * <p>用于抽象常见创建时间、更新时间和逻辑删除标识。</p>
 */
@Data
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}

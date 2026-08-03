package com.tongluxing.chat.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 描述聊天加入申请持久化记录及其当前业务状态。
 * 对象由 MyBatis 在数据库行与 Java 字段之间进行映射。
 */
@Data
public class ChatJoinApplication {
    /** 雪花算法生成的申请主键。 */
    private Long id;
    /** 申请加入的本地会话 ID。 */
    private Long conversationId;
    /** 发起申请的平台用户 ID。 */
    private Long applicantUserId;
    /** 申请人填写的说明，允许为空。 */
    private String applicationMessage;
    /** 申请状态：PENDING、APPROVED 或 REJECTED。 */
    private String applicationStatus;
    /** 实际执行审核的群主用户 ID；待审核时为空。 */
    private Long reviewerUserId;
    /** 审核完成时间；待审核时为空。 */
    private LocalDateTime reviewedAt;
    /** 申请创建时间。 */
    private LocalDateTime createdAt;
    /** 申请最后更新时间。 */
    private LocalDateTime updatedAt;
}

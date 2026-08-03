package com.tongluxing.chat.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import com.tongluxing.chat.entity.MessageRisk;

/** 风险消息只写入审核队列，不提供普通运营人员全量浏览接口。 */
@Mapper
public interface MessageRiskMapper {
    /** 保存消息命中的风险事实，后续由受限的管理端审核队列读取。 */
    @Insert("""
            insert into message_risk
              (id, message_id, risk_level, risk_type, confidence, status, matched_rule, created_at, updated_at)
            values
              (#{id}, #{messageId}, #{riskLevel}, #{riskType}, #{confidence}, #{status}, #{matchedRule}, #{createdAt}, #{updatedAt})
            """)
    void insert(MessageRisk risk);
}

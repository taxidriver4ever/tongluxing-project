/**
 * 匹配模块请求数据传输对象包。
 *
 * <p>DTO 描述搜索与申请接口的输入边界，使用 Jakarta Validation 拦截缺失地点、非法
 * 经纬度、过大分页等格式错误；跨字段规则（例如起止时间顺序、地点不能重复）由
 * Service 在获得完整请求后校验。</p>
 */
package com.tongluxing.match.dto;

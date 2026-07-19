package com.tongluxing.groupbuy.service;

import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.dto.JoinGroupbuyRequest;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.PageResult;

/**
 * 拼团活动业务服务。
 *
 * <p>封装拼团创建、查询、支付入团、过期失败和运营后台干预等核心用例。</p>
 */
public interface GroupbuyService {
    /**
     * 创建拼团活动并预占商品库存。
     *
     * @param request 创建请求，包含幂等 requestId
     * @return 创建后的活动详情
     */
    GroupbuyActivityVO create(CreateGroupbuyRequest request);

    /**
     * 按状态分页查询拼团活动。
     *
     * @param status 活动状态过滤条件，可为空
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<GroupbuyActivityVO> list(String status, int page, int size);
    PageResult<GroupbuyActivityVO> mine(String status, int page, int size);
    PageResult<GroupbuyActivityVO> merchantActivities(String status, int page, int size);

    /**
     * 查询活动详情。
     *
     * @param activityId 活动 ID
     * @return 活动详情及参与人列表
     */
    GroupbuyActivityVO detail(Long activityId);

    /**
     * 记录支付成功的参团用户，并在达到目标人数时自动成团。
     *
     * @param activityId 活动 ID
     * @param request 支付参团请求，包含订单、用户和幂等 requestId
     * @return 更新后的活动详情
     */
    GroupbuyActivityVO addPaidParticipant(Long activityId, PaidParticipantRequest request);
    GroupbuyActivityVO join(Long activityId, JoinGroupbuyRequest request);

    /**
     * 将已过期且未成团的活动标记为失败。
     *
     * @param activityId 活动 ID
     * @return 过期检查后的活动详情
     */
    GroupbuyActivityVO expire(Long activityId);

    /**
     * 应用运营后台干预动作。
     *
     * @param activityId 活动 ID
     * @param action 干预动作，支持 FORCE_SUCCESS、FORCE_FAIL、EXTEND、SUSPEND、CLOSE
     * @param reason 干预原因，供上游审计记录使用
     * @param requestId 幂等请求号
     * @return 干预后的活动详情
     */
    GroupbuyActivityVO applyAdminIntervention(Long activityId, String action, String reason, String requestId, Integer extendMinutes);
}

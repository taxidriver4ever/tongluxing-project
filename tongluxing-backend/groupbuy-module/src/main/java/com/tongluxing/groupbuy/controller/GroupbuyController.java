package com.tongluxing.groupbuy.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.service.GroupbuyService;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 拼团活动对外 REST 接口。
 *
 * <p>负责接收客户端创建、查询、支付入团和手动过期请求，具体业务校验与状态流转交由
 * {@link GroupbuyService} 完成。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/groupbuys")
public class GroupbuyController {
    private final GroupbuyService groupbuyService;

    /**
     * 创建新的拼团活动。
     *
     * @param request 创建拼团所需的商品、人数、有效期和幂等请求号
     * @return 创建后的拼团活动详情
     */
    @PostMapping
    public Result<GroupbuyActivityVO> create(@Valid @RequestBody CreateGroupbuyRequest request) {
        return Result.success(groupbuyService.create(request));
    }

    /**
     * 分页查询拼团活动列表。
     *
     * @param status 可选的活动状态过滤条件，如 ONGOING、SUCCESS、FAILED
     * @param page 页码，从 1 开始
     * @param size 每页条数，服务层会限制合理范围
     * @return 拼团活动分页数据
     */
    @GetMapping
    public Result<PageResult<GroupbuyActivityVO>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(groupbuyService.list(status, page, size));
    }

    /**
     * 查询单个拼团活动详情。
     *
     * @param activityId 拼团活动 ID
     * @return 活动详情及已支付参与人列表
     */
    @GetMapping("/{activityId}")
    public Result<GroupbuyActivityVO> detail(@PathVariable Long activityId) {
        return Result.success(groupbuyService.detail(activityId));
    }

    /**
     * 记录支付成功的参团用户。
     *
     * @param activityId 拼团活动 ID
     * @param request 支付订单、参团用户和幂等请求号
     * @return 更新参团人数与状态后的活动详情
     */
    @PostMapping("/{activityId}/paid-participants")
    public Result<GroupbuyActivityVO> paidParticipant(@PathVariable Long activityId,
                                                      @Valid @RequestBody PaidParticipantRequest request) {
        return Result.success(groupbuyService.addPaidParticipant(activityId, request));
    }

    /**
     * 触发活动过期处理。
     *
     * @param activityId 拼团活动 ID
     * @return 过期检查后的活动详情
     */
    @PostMapping("/{activityId}/expire")
    public Result<GroupbuyActivityVO> expire(@PathVariable Long activityId) {
        return Result.success(groupbuyService.expire(activityId));
    }
}

package com.tongluxing.trip.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.trip.service.TripDraftService;
import com.tongluxing.common.model.PageResult;
import com.tongluxing.user.dto.request.PublishDraftRequest;
import com.tongluxing.user.vo.PublishResultVO;
import com.tongluxing.user.vo.TeamMatchVO;
import com.tongluxing.user.dto.request.TripDraftRequest;
import com.tongluxing.user.vo.TripDraftVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 行程草稿接口控制器，承接“下一趟行程”草稿创建、编辑、发布和推荐车队查询。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trip-drafts")
public class TripDraftController {
    private final TripDraftService service;

    /**
     * 创建一条行程草稿。
     */
    @PostMapping
    public Result<TripDraftVO> create(@Valid @RequestBody TripDraftRequest request) {
        return Result.success(service.create(request));
    }

    /**
     * 分页查询当前用户的行程草稿。
     */
    @GetMapping
    public Result<PageResult<TripDraftVO>> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(status, page, size));
    }

    /**
     * 更新草稿内容，仅允许可编辑状态的草稿修改。
     */
    @PutMapping("/{id}")
    public Result<TripDraftVO> update(@PathVariable Long id, @Valid @RequestBody TripDraftRequest request) {
        return Result.success(service.update(id, request));
    }

    /**
     * 删除草稿，仅允许可删除状态的草稿删除。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    /**
     * 发布草稿为行程或车队。
     */
    @PostMapping("/{id}/publish")
    public Result<PublishResultVO> publish(@PathVariable Long id, @Valid @RequestBody PublishDraftRequest request) {
        return Result.success(service.publish(id, request.publishType()));
    }

    /**
     * 基于草稿位置和时间查询推荐车队。
     */
    @GetMapping("/{id}/team-recommendations")
    public Result<PageResult<TeamMatchVO>> recommendations(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.recommendTeams(id, page, size));
    }
}

package com.tongdao.trip.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.trip.service.TripDraftService;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublishDraftRequest;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trip-drafts")
public class TripDraftController {
    private final TripDraftService service;

    @PostMapping
    public Result<TripDraftVO> create(@Valid @RequestBody TripDraftRequest request) { return Result.success(service.create(request)); }

    @GetMapping
    public Result<PageResult<TripDraftVO>> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(status, page, size));
    }

    @PutMapping("/{id}")
    public Result<TripDraftVO> update(@PathVariable Long id, @Valid @RequestBody TripDraftRequest request) {
        return Result.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.success(); }

    @PostMapping("/{id}/publish")
    public Result<PublishResultVO> publish(@PathVariable Long id, @Valid @RequestBody PublishDraftRequest request) {
        return Result.success(service.publish(id, request.publishType()));
    }

    @GetMapping("/{id}/team-recommendations")
    public Result<PageResult<TeamMatchVO>> recommendations(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.recommendTeams(id, page, size));
    }
}

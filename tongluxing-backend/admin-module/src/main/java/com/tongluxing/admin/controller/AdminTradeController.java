package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.admin.service.AdminTradeService;
import com.tongluxing.admin.vo.*;
import com.tongluxing.common.result.Result;
import lombok.RequiredArgsConstructor;

/** 核销记录与交易流水查询。 */
@RestController @RequiredArgsConstructor @RequestMapping("/v1/admin")
public class AdminTradeController {
    private final AdminTradeService service;
    @GetMapping("/verifications/records") public Result<PageResult<AdminVerificationRecordVO>> verifications(
        @RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.verifications(status,page,size));}
    @GetMapping("/transactions") public Result<PageResult<AdminTransactionVO>> transactions(
        @RequestParam(required=false) String type,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.transactions(type,page,size));}
}

package com.tongluxing.merchant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.Result;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.merchant.mapper.MerchantSettlementQueryMapper;
import com.tongluxing.merchant.vo.MerchantSettlementVO;
import com.tongluxing.merchant.vo.PageResult;
import com.tongluxing.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;

/** 商家查看本店结算记录，不接收前端 merchantId。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants/settlements")
public class MerchantSettlementController {
    private final MerchantSettlementQueryMapper mapper;
    private final CurrentUserContext currentUser;

    @GetMapping
    public Result<PageResult<MerchantSettlementVO>> page(@RequestParam(required=false) String status,
                                                         @RequestParam(defaultValue="1") int page,
                                                         @RequestParam(defaultValue="20") int size) {
        Long merchantId=mapper.merchantId(currentUser.requireUserId());
        if(merchantId==null) throw new BusinessException(ResultCode.FORBIDDEN,"当前账号不是可经营商家");
        page=Math.max(1,page);size=Math.min(100,Math.max(1,size));
        return Result.success(new PageResult<>(mapper.page(merchantId,status,(page-1)*size,size),
                mapper.count(merchantId,status),page,size));
    }
}

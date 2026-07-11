package com.tongluxing.customerservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.user.model.UserModels.CustomerServiceEntryVO;

/**
 * 客服入口接口。
 *
 * <p>该接口只返回前端进入客服能力所需的轻量配置，例如渠道类型和客服账号标识。
 * 真实问题处理闭环由工单接口承载；后续接入微信客服时，也可以继续保留该入口作为前端路由配置来源。</p>
 */
@RestController
@RequestMapping("/v1/customer-service")
public class CustomerServiceController {

    /**
     * 获取客服入口配置。
     *
     * @param scene 入口场景，例如 USER_CENTER、ORDER_DETAIL；用于前端统计和展示不同入口文案
     * @return 客服渠道和客服账号标识
     */
    @GetMapping("/entry")
    public Result<CustomerServiceEntryVO> entry(@RequestParam(defaultValue = "USER_CENTER") String scene) {
        return Result.success(new CustomerServiceEntryVO(scene, "WECHAT", "tongluxing-service"));
    }
}

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
 * <p>当前模块仅提供前端进入客服能力所需的轻量入口信息，
 * 尚未承载客服会话、工单、人工坐席分配等完整客服业务流程。</p>
 */
@RestController
@RequestMapping("/v1/customer-service")
public class CustomerServiceController {

    /**
     * 获取客服入口配置。
     *
     * @param scene 入口场景，例如 USER_CENTER；用于前端区分从哪个页面进入客服
     * @return 客服渠道和客服账号标识
     */
    @GetMapping("/entry")
    public Result<CustomerServiceEntryVO> entry(@RequestParam(defaultValue = "USER_CENTER") String scene) {
        return Result.success(new CustomerServiceEntryVO(scene, "WECHAT", "tongluxing-service"));
    }
}

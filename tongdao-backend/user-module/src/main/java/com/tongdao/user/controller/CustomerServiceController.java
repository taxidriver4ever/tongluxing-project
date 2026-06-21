package com.tongdao.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.user.model.UserModels.CustomerServiceEntryVO;

@RestController
@RequestMapping("/v1/customer-service")
public class CustomerServiceController {
    @GetMapping("/entry")
    public Result<CustomerServiceEntryVO> entry(@RequestParam(defaultValue="USER_CENTER") String scene) {
        return Result.success(new CustomerServiceEntryVO(scene, "WECHAT", "tongdao-service"));
    }
}

package com.tongdao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.tongdao.auth.mapper",
        "com.tongdao.user.mapper",
        "com.tongdao.growth.mapper",
        "com.tongdao.invite.mapper",
        "com.tongdao.coupon.mapper",
        "com.tongdao.vehicle.mapper",
        "com.tongdao.trip.mapper",
        "com.tongdao.match.mapper",
        "com.tongdao.team.mapper",
        "com.tongdao.chat.mapper",
        "com.tongdao.map.mapper",
        "com.tongdao.merchant.mapper",
        "com.tongdao.groupbuy.mapper",
        "com.tongdao.order.mapper",
        "com.tongdao.payment.mapper",
        "com.tongdao.verification.mapper",
        "com.tongdao.assessment.mapper",
        "com.tongdao.customerservice.mapper",
        "com.tongdao.notify.mapper",
        "com.tongdao.admin.mapper"
})
@SpringBootApplication(scanBasePackages = "com.tongdao")
/**
 * 同道后端统一启动入口。
 *
 * <p>该应用层模块负责装配各业务模块，并统一声明 MyBatis Mapper 扫描范围。</p>
 */
public class TongdaoApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(TongdaoApplication.class, args);
    }
}

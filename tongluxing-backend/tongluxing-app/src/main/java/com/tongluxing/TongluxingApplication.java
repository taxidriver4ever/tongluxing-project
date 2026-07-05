package com.tongluxing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.tongluxing.auth.mapper",
        "com.tongluxing.user.mapper",
        "com.tongluxing.growth.mapper",
        "com.tongluxing.invite.mapper",
        "com.tongluxing.coupon.mapper",
        "com.tongluxing.vehicle.mapper",
        "com.tongluxing.trip.mapper",
        "com.tongluxing.match.mapper",
        "com.tongluxing.team.mapper",
        "com.tongluxing.chat.mapper",
        "com.tongluxing.map.mapper",
        "com.tongluxing.merchant.mapper",
        "com.tongluxing.groupbuy.mapper",
        "com.tongluxing.order.mapper",
        "com.tongluxing.payment.mapper",
        "com.tongluxing.verification.mapper",
        "com.tongluxing.assessment.mapper",
        "com.tongluxing.customerservice.mapper",
        "com.tongluxing.notify.mapper",
        "com.tongluxing.admin.mapper",
        "com.tongluxing.storage.mapper"
})
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.tongluxing")
/**
 * 同路行后端统一启动入口。
 *
 * <p>该应用层模块负责装配各业务模块，并统一声明 MyBatis Mapper 扫描范围。</p>
 */
public class TongluxingApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(TongluxingApplication.class, args);
    }
}

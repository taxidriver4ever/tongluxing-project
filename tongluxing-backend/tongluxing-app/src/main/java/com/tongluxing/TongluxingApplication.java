package com.tongluxing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 同路行后端统一启动入口。
 *
 * <p>该应用层模块负责装配各业务模块，开启定时任务，并统一声明 MyBatis Mapper 扫描范围。</p>
 */
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
        "com.tongluxing.drivertrack.mapper",
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
public class TongluxingApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TongluxingApplication.class, args);
    }
}

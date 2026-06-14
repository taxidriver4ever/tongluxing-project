package com.tongdao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.tongdao.auth.mapper",
        "com.tongdao.user.mapper",
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
public class TongdaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongdaoApplication.class, args);
    }
}

package com.tongdao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.tongdao.auth.mapper",
        "com.tongdao.activity.mapper",
        "com.tongdao.order.mapper"
})
@SpringBootApplication(scanBasePackages = "com.tongdao")
public class TongdaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongdaoApplication.class, args);
    }
}

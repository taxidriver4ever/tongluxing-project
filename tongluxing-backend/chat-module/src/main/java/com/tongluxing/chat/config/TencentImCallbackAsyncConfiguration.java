package com.tongluxing.chat.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 腾讯 IM 回调专用异步执行器，和通用业务异步任务隔离。 */
@Configuration
@EnableAsync
public class TencentImCallbackAsyncConfiguration {

    @Bean("tencentImCallbackExecutor")
    public Executor tencentImCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("tencent-im-callback-");
        // 极端拥塞时允许请求线程退化执行，但绝不能静默丢失 Callback。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

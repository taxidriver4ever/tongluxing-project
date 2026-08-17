package com.tongluxing.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 行程领域事件异步执行器。
 *
 * <p>建群、建队、推荐生成、统计等都不是发布/结束接口返回前必须完成的主事务，
 * 放到事务提交后异步执行，避免第三方 IM 或批量推荐计算阻塞用户请求线程。</p>
 */
@Configuration
@EnableAsync
public class AsyncEventConfiguration {

    @Bean(name = "tripEventExecutor")
    public Executor tripEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("trip-event-");
        // 队列极端饱和时由发布线程兜底执行，宁可退化为同步也不丢失建群/统计等业务事件。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}

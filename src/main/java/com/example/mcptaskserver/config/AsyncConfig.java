package com.example.mcptaskserver.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous task execution.
 * Thread pool parameters are configurable via application.yml under 'async.batch'.
 */
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "async.batch")
@Getter
@Setter
@Slf4j
public class AsyncConfig {
    
    private int corePoolSize = 5;
    private int maxPoolSize = 10;
    private int queueCapacity = 100;
    private int terminationSeconds = 60;

    @Bean(name = "batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("batch-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(terminationSeconds);
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Batch task rejected, queue is full. Consider reducing batch frequency.");
            throw new RuntimeException("Batch processing queue is full. Please wait and retry.");
        });
        executor.initialize();
        
        log.info("Batch executor configured: core={}, max={}, queue={}, terminationSeconds={}",
            corePoolSize, maxPoolSize, queueCapacity, terminationSeconds);
        
        return executor;
    }
}

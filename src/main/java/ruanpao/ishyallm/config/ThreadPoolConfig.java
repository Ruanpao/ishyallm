package ruanpao.ishyallm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("cpuPool")
    public ThreadPoolExecutor cpuPool() {
        return new ThreadPoolExecutor(
                2,                          // corePoolSize
                2,                          // maxPoolSize
                60, TimeUnit.SECONDS,       // keepAliveTime
                new ArrayBlockingQueue<>(100),  // 有界队列
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }

    @Bean("ioPool")
    public ThreadPoolExecutor ioPool() {
        return new ThreadPoolExecutor(
                4,                          // corePoolSize
                8,                          // maxPoolSize
                60, TimeUnit.SECONDS,       // keepAliveTime
                new ArrayBlockingQueue<>(200),  // 有界队列
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }
}

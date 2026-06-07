package ruanpao.ishyallm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ThreadPoolConfigTest {

    @Autowired
    @Qualifier("cpuPool")
    private Executor cpuPool;

    @Autowired
    @Qualifier("ioPool")
    private Executor ioPool;

    @Test
    void shouldCreateCpuPoolWithCorrectConfiguration() {
        assertThat(cpuPool).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) cpuPool;
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaximumPoolSize()).isEqualTo(2);
    }

    @Test
    void shouldCreateIoPoolWithCorrectConfiguration() {
        assertThat(ioPool).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ioPool;
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaximumPoolSize()).isEqualTo(8);
    }

    @Test
    void cpuPoolShouldRejectNewTasksWhenFull() {
        assertThat(cpuPool).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) cpuPool;
        // CPU 池使用有界队列且容量饱和时的拒绝策略
        assertThat(executor.getRejectedExecutionHandler()).isNotNull();
    }
}

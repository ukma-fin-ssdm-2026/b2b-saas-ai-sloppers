package ua.edu.ukma.community.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Фонові задачі, які не мають тримати HTTP-запит (Lab 09, R1 і R2).
 *
 * <p>Кожна задача має свій <b>обмежений</b> пул: фіксована кількість потоків і черга зі стелею.
 * Якщо черга переповнена, задача відкидається з WARN у лог (fail safe), а не блокує запит
 * і не створює нових потоків без кінця. Розсилка й прибирання файлів розділені, щоб тисяча листів
 * не затримувала прибирання сховища, і навпаки.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** Пул для листів підписникам про нову подію (R1). */
    public static final String ANNOUNCEMENTS = "announcementExecutor";
    /** Пул для прибирання файлів у Supabase Storage після видалення організації (R2). */
    public static final String STORAGE_CLEANUP = "storageCleanupExecutor";

    static final int ANNOUNCEMENT_THREADS = 2;
    static final int ANNOUNCEMENT_QUEUE = 500;
    static final int CLEANUP_THREADS = 1;
    static final int CLEANUP_QUEUE = 100;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(ANNOUNCEMENTS)
    ThreadPoolTaskExecutor announcementExecutor() {
        return boundedExecutor("announce-", ANNOUNCEMENT_THREADS, ANNOUNCEMENT_QUEUE);
    }

    @Bean(STORAGE_CLEANUP)
    ThreadPoolTaskExecutor storageCleanupExecutor() {
        return boundedExecutor("storage-cleanup-", CLEANUP_THREADS, CLEANUP_QUEUE);
    }

    /** Виняток у {@code void}-методі з {@code @Async} інакше зник би мовчки: пишемо його в ERROR. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, params) -> LOG.error("Background task {}.{} failed",
                method.getDeclaringClass().getSimpleName(), method.getName(), error);
    }

    static ThreadPoolTaskExecutor boundedExecutor(String threadPrefix, int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadPrefix);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(dropAndLog(threadPrefix));
        // при зупинці поду даємо дописати листи, що вже в черзі
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    static RejectedExecutionHandler dropAndLog(String pool) {
        return (task, executor) -> LOG.warn("Background pool '{}' is full ({} queued): task dropped",
                pool, queued(executor));
    }

    private static int queued(ThreadPoolExecutor executor) {
        return executor.getQueue().size();
    }
}

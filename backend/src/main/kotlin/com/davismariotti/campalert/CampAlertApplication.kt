package com.davismariotti.campalert

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.task.TaskExecutor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor
import javax.sql.DataSource

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableSchedulerLock(defaultLockAtMostFor = "PT90S")
@EnableAsync
@EnableScheduling
class CampFinderApplication {
    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build(),
        )

    @Bean("availabilityCheckerExecutor")
    fun availabilityCheckerExecutor(
        @Value("\${campfinder.checker.thread-pool-size:20}") poolSize: Int,
        @Value("\${campfinder.checker.thread-pool-queue-capacity:100}") queueCapacity: Int,
    ): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = poolSize
            maxPoolSize = poolSize
            this.queueCapacity = queueCapacity
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setThreadNamePrefix("availability-checker-")
            initialize()
        }

    @Bean("timezoneResolutionExecutor")
    fun timezoneResolutionExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            queueCapacity = 50
            setThreadNamePrefix("tz-resolution-")
            initialize()
        }

    @Bean("campLifeCatalogExecutor")
    fun campLifeCatalogExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 10
            setThreadNamePrefix("camplife-catalog-")
            initialize()
        }

    @Bean("reserveCaliforniaCatalogExecutor")
    fun reserveCaliforniaCatalogExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 10
            setThreadNamePrefix("reserveca-catalog-")
            initialize()
        }

    /**
     * One task per facility currently warming up (design.md D14) — a facility's fan-out loops
     * through its PENDING units sequentially, paced by reserveCaliforniaWarmupCallProtection's rate
     * limiter, so this pool's size bounds how many facilities can warm up concurrently, not how fast
     * any single facility warms up.
     */
    @Bean("reserveCaliforniaOccupancyExecutor")
    fun reserveCaliforniaOccupancyExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 50
            setThreadNamePrefix("reserveca-occupancy-")
            initialize()
        }
}

fun main(args: Array<String>) {
    runApplication<CampFinderApplication>(*args)
}

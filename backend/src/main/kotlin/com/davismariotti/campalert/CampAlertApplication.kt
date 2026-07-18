package com.davismariotti.campalert

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
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
        meterRegistry: MeterRegistry,
    ): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = poolSize
            maxPoolSize = poolSize
            this.queueCapacity = queueCapacity
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setThreadNamePrefix("availability-checker-")
            initialize()
            ExecutorServiceMetrics.monitor(meterRegistry, threadPoolExecutor, "availability-checker")
        }

    @Bean("timezoneResolutionExecutor")
    fun timezoneResolutionExecutor(meterRegistry: MeterRegistry): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            queueCapacity = 50
            setThreadNamePrefix("tz-resolution-")
            initialize()
            ExecutorServiceMetrics.monitor(meterRegistry, threadPoolExecutor, "tz-resolution")
        }

    @Bean("campLifeCatalogExecutor")
    fun campLifeCatalogExecutor(meterRegistry: MeterRegistry): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 10
            setThreadNamePrefix("camplife-catalog-")
            initialize()
            ExecutorServiceMetrics.monitor(meterRegistry, threadPoolExecutor, "camplife-catalog")
        }
}

fun main(args: Array<String>) {
    runApplication<CampFinderApplication>(*args)
}

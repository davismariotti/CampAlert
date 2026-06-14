package com.davismariotti.campalert

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.task.TaskExecutor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import javax.sql.DataSource

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableSchedulerLock(defaultLockAtMostFor = "PT90S")
@EnableAsync
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

    @Bean("timezoneResolutionExecutor")
    fun timezoneResolutionExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            queueCapacity = 50
            setThreadNamePrefix("tz-resolution-")
            initialize()
        }
}

fun main(args: Array<String>) {
    runApplication<CampFinderApplication>(*args)
}

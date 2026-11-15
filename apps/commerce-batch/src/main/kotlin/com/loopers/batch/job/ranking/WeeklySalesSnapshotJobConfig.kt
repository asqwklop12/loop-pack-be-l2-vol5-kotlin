package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.WeeklySalesSnapshotTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklySalesSnapshotJobConfig.JOB_NAME)
@Configuration
class WeeklySalesSnapshotJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val weeklySalesSnapshotTasklet: WeeklySalesSnapshotTasklet,
) {
    companion object {
        const val JOB_NAME = "weeklySalesSnapshotJob"
        private const val STEP_NAME = "weeklySalesSnapshotStep"
    }

    @Bean(JOB_NAME)
    fun weeklySalesSnapshotJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(weeklySalesSnapshotStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun weeklySalesSnapshotStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(weeklySalesSnapshotTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
}

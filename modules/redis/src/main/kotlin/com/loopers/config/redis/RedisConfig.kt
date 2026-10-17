package com.loopers.config.redis

import io.lettuce.core.ReadFrom
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfig(
    private val redisProperties: RedisProperties,
) {
    companion object {
        private const val CONNECTION_MASTER = "redisConnectionMaster"
        const val REDIS_TEMPLATE_MASTER = "redisTemplateMaster"
    }

    @Primary
    @Bean
    fun defaultRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas) = redisProperties
        return lettuceConnectionFactory(database, master, replicas) {
            readFrom(ReadFrom.REPLICA_PREFERRED)
        }
    }

    @Qualifier(CONNECTION_MASTER)
    @Bean
    fun masterRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas) = redisProperties
        return lettuceConnectionFactory(database, master, replicas) {
            readFrom(ReadFrom.MASTER)
        }
    }

    @Primary
    @Bean
    fun defaultRedisTemplate(
        lettuceConnectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<*, *> {
        return RedisTemplate<String, String>()
            .defaultRedisTemplate(lettuceConnectionFactory)
    }

    @Qualifier(REDIS_TEMPLATE_MASTER)
    @Bean
    fun masterRedisTemplate(
        @Qualifier(CONNECTION_MASTER) lettuceConnectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<*, *> {
        return RedisTemplate<String, String>()
            .defaultRedisTemplate(lettuceConnectionFactory)
    }

    private fun lettuceConnectionFactory(
        database: Int,
        master: RedisNodeInfo,
        replicas: List<RedisNodeInfo>,
        customizer: LettuceClientConfiguration.LettuceClientConfigurationBuilder.() -> Unit = {},
    ): LettuceConnectionFactory {
        val lettuceClientConfiguration = LettuceClientConfiguration.builder()
            .apply(customizer)
            .build()
        val masterReplicaConfig = RedisStaticMasterReplicaConfiguration(master.host, master.port)
        masterReplicaConfig.database = database
        replicas.forEach { replica ->
            masterReplicaConfig.addNode(replica.host, replica.port)
        }

        return LettuceConnectionFactory(masterReplicaConfig, lettuceClientConfiguration).apply {
            /*
             * Redis 가 끊겼다 돌아와도 스스로 복구하게 한다.
             *
             * 기본값은 연결 하나를 공유하면서(`shareNativeConnection = true`) 그 연결이
             * 살아 있는지 확인하지 않는다(`validateConnection = false`). 그래서 한번 끊기면
             * 죽은 연결을 계속 내어주고, Redis 가 다시 떠도 앱을 재시작하기 전까지 복구되지 않는다.
             *
             * 켜면 연결을 내어주기 전에 `PING` 으로 확인하고, 죽어 있으면 새로 만든다.
             * `PING` 한 번이 더 들지만 끊긴 뒤 영원히 캐시를 못 쓰는 것보다 싸다.
             */
            validateConnection = true
        }
    }

    private fun <K, V> RedisTemplate<K, V>.defaultRedisTemplate(
        connectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<K, V> {
        this.keySerializer = StringRedisSerializer()
        this.valueSerializer = StringRedisSerializer()
        this.hashKeySerializer = StringRedisSerializer()
        this.hashValueSerializer = StringRedisSerializer()
        setConnectionFactory(connectionFactory)
        return this
    }
}

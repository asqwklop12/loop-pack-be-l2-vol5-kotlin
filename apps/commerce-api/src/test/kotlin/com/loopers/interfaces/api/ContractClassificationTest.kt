package com.loopers.interfaces.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.example.ExampleModel
import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.io.File

/**
 * W1 관찰 테스트.
 *
 * 주문 할인 기능을 구현하지 않는다. 기존 `ExampleV1Controller` 에 네 가지 입력을 실제로 보내
 * 이 저장소가 지금 성공과 오류를 어떤 모양으로 표현하는지 기록한다.
 * 관찰한 값은 `docs/week1/order-discount-contract.md` 3절 표에 그대로 옮긴다.
 *
 * 작성할 네 입력:
 *   1. 존재하는 숫자 ID   -> /api/v1/examples/{저장된 id}
 *   2. 숫자가 아닌 ID     -> /api/v1/examples/abc
 *   3. 없는 숫자 ID       -> /api/v1/examples/-1
 *   4. 미매핑 URL         -> /api/v1/not-mapped-url
 *
 * 각 입력마다 HTTP status / meta.result / meta.errorCode / data 유무를 assertion 한다.
 *
 * 관찰 결과는 콘솔과 `apps/commerce-api/build/contract-observation.md` 양쪽에 남는다.
 * 콘솔로 보려면 `-i` 를 붙인다.
 *   ./gradlew :apps:commerce-api:test --tests 'com.loopers.interfaces.api.ContractClassificationTest' -i
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractClassificationTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val exampleJpaRepository: ExampleJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val objectMapper = ObjectMapper()

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("[관찰 1] 존재하는 숫자 ID — 정상 조회")
    @Test
    fun observe1_existingNumericId() {
        val saved = exampleJpaRepository.save(ExampleModel(name = "관찰 대상", description = "관찰용 설명"))
        val observed = observe("1", "존재하는 숫자 ID", "/api/v1/examples/${saved.id}")

        assertAll(
            { assertThat(observed.status).isEqualTo(HttpStatus.OK.value()) },
            { assertThat(observed.result).isEqualTo("SUCCESS") },
            { assertThat(observed.errorCode).isNull() },
            { assertThat(observed.hasData).isTrue() },
        )
    }

    @DisplayName("[관찰 2] 숫자가 아닌 ID — 문법 오류")
    @Test
    fun observe2_nonNumericId() {
        val observed = observe("2", "숫자가 아닌 ID", "/api/v1/examples/abc")

        assertAll(
            { assertThat(observed.status).isEqualTo(HttpStatus.BAD_REQUEST.value()) },
            { assertThat(observed.result).isEqualTo("FAIL") },
            { assertThat(observed.errorCode).isEqualTo(HttpStatus.BAD_REQUEST.reasonPhrase) },
            { assertThat(observed.hasData).isFalse() },
        )
    }

    @DisplayName("[관찰 3] 없는 숫자 ID — 대상 자원 없음")
    @Test
    fun observe3_missingNumericId() {
        val observed = observe("3", "없는 숫자 ID", "/api/v1/examples/-1")

        assertAll(
            { assertThat(observed.status).isEqualTo(HttpStatus.NOT_FOUND.value()) },
            { assertThat(observed.result).isEqualTo("FAIL") },
            { assertThat(observed.errorCode).isEqualTo(HttpStatus.NOT_FOUND.reasonPhrase) },
            { assertThat(observed.hasData).isFalse() },
        )
    }

    @DisplayName("[관찰 4] 미매핑 URL — 요청 처리기 없음")
    @Test
    fun observe4_notMappedUrl() {
        val observed = observe("4", "미매핑 URL", "/api/v1/not-mapped-url")

        assertAll(
            { assertThat(observed.status).isEqualTo(HttpStatus.NOT_FOUND.value()) },
            { assertThat(observed.result).isEqualTo("FAIL") },
            { assertThat(observed.errorCode).isEqualTo(HttpStatus.NOT_FOUND.reasonPhrase) },
            { assertThat(observed.hasData).isFalse() },
        )
    }

    /**
     * 실제 HTTP 로 요청을 보내고 응답을 그대로 기록한다.
     * assertion 보다 먼저 기록하므로, assertion 이 실패해도 관찰값은 남는다.
     */
    private fun observe(no: String, label: String, url: String): Observed {
        val response = testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity<Any>(Unit), String::class.java)
        val body = response.body

        val meta = body?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
        val dataNode = meta?.get("data")

        val observed = Observed(
            no = no,
            label = label,
            url = url,
            status = response.statusCode.value(),
            result = meta?.get("meta")?.get("result")?.takeIf { !it.isNull }?.asText(),
            errorCode = meta?.get("meta")?.get("errorCode")?.takeIf { !it.isNull }?.asText(),
            message = meta?.get("meta")?.get("message")?.takeIf { !it.isNull }?.asText(),
            hasData = dataNode != null && !dataNode.isNull,
            rawBody = body,
        )
        record(observed)
        return observed
    }

    data class Observed(
        val no: String,
        val label: String,
        val url: String,
        val status: Int,
        val result: String?,
        val errorCode: String?,
        val message: String?,
        val hasData: Boolean,
        val rawBody: String?,
    )

    companion object {
        private val observations = mutableListOf<Observed>()

        private fun record(observed: Observed) {
            observations += observed
            println(
                """
                |
                |---------- 관찰 ${observed.no}. ${observed.label} ----------
                |url        : ${observed.url}
                |status     : ${observed.status}
                |result     : ${observed.result ?: "(없음)"}
                |errorCode  : ${observed.errorCode ?: "(없음)"}
                |message    : ${observed.message ?: "(없음)"}
                |data       : ${if (observed.hasData) "있음" else "없음"}
                |body       : ${observed.rawBody ?: "(빈 본문)"}
                """.trimMargin(),
            )
        }

        @JvmStatic
        @AfterAll
        fun writeReport() {
            if (observations.isEmpty()) return
            val sorted = observations.sortedBy { it.no }

            val report = buildString {
                appendLine("# 관찰 결과 (ContractClassificationTest)")
                appendLine()
                appendLine("`docs/week1/order-discount-contract.md` 3절 표에 그대로 옮긴다.")
                appendLine()
                appendLine("| # | 입력 | HTTP status | `meta.result` | `meta.errorCode` | `data` |")
                appendLine("|---|---|---|---|---|---|")
                sorted.forEach {
                    appendLine(
                        "| ${it.no} | `${it.url}` | ${it.status} | ${it.result ?: "-"} | " +
                            "${it.errorCode ?: "-"} | ${if (it.hasData) "있음" else "없음"} |",
                    )
                }
                appendLine()
                appendLine("## 응답 body 원문")
                appendLine()
                appendLine("```")
                sorted.forEach {
                    appendLine("[${it.no}] ${it.rawBody ?: "(빈 본문)"}")
                }
                appendLine("```")
                appendLine()
                appendLine("## message 까지 포함한 대조")
                appendLine()
                appendLine("| # | status | errorCode | message |")
                appendLine("|---|---|---|---|")
                sorted.forEach {
                    appendLine("| ${it.no} | ${it.status} | ${it.errorCode ?: "-"} | ${it.message ?: "-"} |")
                }
                appendLine()
                appendLine("> 3번(없는 자원)과 4번(미매핑 URL)이 message 까지 같으면 외부에서 구별할 수 없다.")
                appendLine("> INV-001 의 기대 결과가 이 값에 달려 있다.")
            }

            val file = File("build/contract-observation.md")
            file.parentFile?.mkdirs()
            file.writeText(report)
            println("\n관찰 결과를 기록했다: ${file.absolutePath}\n")
            println(report)
        }
    }
}

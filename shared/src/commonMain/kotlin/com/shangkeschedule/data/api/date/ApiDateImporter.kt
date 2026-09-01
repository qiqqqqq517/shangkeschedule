package com.shangkeschedule.data.api.date

import com.shangkeschedule.data.repository.AppSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiResponse(
    @SerialName("holiday")
    val holidays: Map<String, HolidayInfo>
)

@Serializable
data class HolidayInfo(
    @SerialName("date")
    val date: String,
    @SerialName("holiday")
    val isHoliday: Boolean
)

/**
 * API 导入对象，基于 Ktor 3.0 实现。
 */
object ApiDateImporter {
    private const val BASE_URL = "https://timor.tech/api/holiday/year"

    private val client = HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.DEFAULT
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }

        defaultRequest {
            url(BASE_URL)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
        }
    }

    /**
     * 从 API 获取跳过的日期（假期），并保存到 AppSettingsRepository 中。
     * 与本地已记录的手工调休/停课日期做「合并」而非整体覆盖，避免冲掉用户手动维护的条目。
     */
    suspend fun importAndSaveSkippedDates(appSettingsRepository: AppSettingsRepository) {
        try {
            val response: ApiResponse = client.get("").body()

            val holidayDates = response.holidays.values
                .filter { it.isHoliday }
                .map { it.date }
                .toSet()

            val currentSettings = appSettingsRepository.getAppSettings().first()
            val mergedSkippedDates = currentSettings.skippedDates + holidayDates
            val updatedSettings = currentSettings.copy(skippedDates = mergedSkippedDates)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)

            println("成功导入并合并了 ${holidayDates.size} 个假期日期（现共 ${mergedSkippedDates.size} 个跳过日期）。")
        } catch (e: Exception) {
            println("数据导入失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun close() = client.close()
}
package com.shangkeschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shangkeschedule.data.model.CategoryLastSchool
import com.shangkeschedule.data.model.SchoolCategoryTab
import com.shangkeschedule.data.model.SchoolHistoryModel
import com.shangkeschedule.tool.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import school_index.Adapter
import school_index.AdapterCategory
import school_index.School
import school_index.SchoolIndex
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

private const val TAG = "SchoolRepository"

/**
 * 学校数据仓库。
 * 职责：处理内部存储中 Protobuf 学校索引文件的读取与解析。
 */
@Single
class SchoolRepository(
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path
) {

    // 定义需要在一级菜单中显示的教务类别。
    // 由分类口径（SchoolCategoryTab）反推，避免「口径清单」和「类别清单」两处各写一份而对不上。
    private val RELEVANT_MENU_CATEGORIES: Set<AdapterCategory> =
        SchoolCategoryTab.entries.flatMapTo(mutableSetOf()) { it.categories }

    /**
     * 索引内存缓存：避免每次查询都重新读盘并 decode 全量 school_index.pb。
     * 以文件 size + 修改时间作为缓存键，离线资源重新解压后自动失效，无需手动清理。
     * Mutex 串行化缓存读写（suspend 友好，KMP 兼容）。
     */
    private var cachedIndex: SchoolIndex? = null
    private var cacheKey: Pair<Long, Long>? = null
    private val cacheMutex = Mutex()

    /**
     * 核心加载函数：优先命中内存缓存，未命中才从内部存储文件读取 Protobuf 索引。
     */
    private suspend fun loadIndex(): SchoolIndex? {
        val internalPath = filesDir / "repo/index/school_index.pb"

        if (!fileSystem.exists(internalPath)) {
            AppLog.e(TAG, "Protobuf 索引文件未找到: $internalPath")
            return null
        }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val metadata = try {
                    fileSystem.metadataOrNull(internalPath)
                } catch (_: Exception) {
                    null
                }
                val currentKey = metadata?.let { it.size?.let { size -> size to (it.lastModifiedAtMillis ?: 0L) } }

                if (currentKey != null && currentKey == cacheKey) {
                    return@withContext cachedIndex
                }

                try {
                    val decoded = fileSystem.source(internalPath).use { source ->
                        SchoolIndex.ADAPTER.decode(source.buffer())
                    }
                    cachedIndex = decoded
                    cacheKey = currentKey
                    decoded
                } catch (e: Exception) {
                    AppLog.e(TAG, "学校索引解码失败", e)
                    null
                }
            }
        }
    }

    /**
     * 【一级页面数据】获取经过类别过滤的学校列表。
     */
    suspend fun getSchools(): List<School> {
        val index = loadIndex() ?: return emptyList()

        // 1. 过滤：使用 Wire 生成的直接列表属性名
        val filteredSchools = index.schools.filter { school ->
            school.adapters.any { adapter ->
                adapter.category in RELEVANT_MENU_CATEGORIES
            }
        }

        // 2. 排序：initial 字段在 Wire 中保留了 proto 定义的原样
        return filteredSchools.sortedBy { it.initial.uppercase() + it.name }
    }

    /**
     * 【二级页面数据】根据学校 ID 获取其所有的适配器列表。
     */
    suspend fun getAdaptersForSchool(schoolId: String): List<Adapter> {
        return withContext(Dispatchers.IO) {
            val index = loadIndex()
            val school = index?.schools?.find { it.id == schoolId }
            return@withContext school?.adapters ?: emptyList()
        }
    }

    /**
     * 【二级页面数据】根据一组学校 ID 获取这些**记录本身**（v4.73.0）。
     *
     * 为什么要「记录」而不是「适配器」：同一所学校在索引里可能拆成本科、研究生两条记录，
     * 分属不同 `resource_folder`；适配脚本路径按 `<resource_folder>/<脚本名>` 拼接，
     * 只拿适配器就丢了「它属于哪条记录」这一信息，脚本路径会拼错。
     *
     * [ids] 的顺序即返回顺序（调用方传主记录在前，保证展示顺序稳定）。
     */
    suspend fun getSchoolsByIds(ids: List<String>): List<School> {
        if (ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val index = loadIndex() ?: return@withContext emptyList()
            // 按请求顺序返回，避免依赖索引内部顺序
            ids.mapNotNull { id -> index.schools.find { it.id == id } }
        }
    }

}


/**
 * 用户记录仓库
 *
 */
@Single
class SchoolHistoryRepository(
    @Named("SchoolHistory") private val dataStore: DataStore<Preferences>
) {
    val historyFlow: Flow<SchoolHistoryModel> = dataStore.data.map { prefs ->
        SchoolHistoryModel.fromPreferences(prefs)
    }

    /**
     * 保存上次选择的学校。
     *
     * v4.73.0：入参改为业务模型 [CategoryLastSchool] 而非 wire 的 [School] ——
     * 选校列表现在按校名合并成 [SchoolListEntry]，写入时取主记录转换而来；
     * 仓库层本就不该依赖 wire 生成类型（`getSchools()` 除外，它必须读原始索引）。
     */
    suspend fun saveLastSchool(tab: SchoolCategoryTab, school: CategoryLastSchool) {
        dataStore.edit { prefs ->
            val keys = SchoolHistoryModel.getPrimaryKeys(tab)
            prefs[keys.id] = school.id
            prefs[keys.name] = school.name
            prefs[keys.folder] = school.resourceFolder
        }
    }

    /**
     * 清除历史记录（含该口径下旧版本遗留的槽位，见 [SchoolHistoryModel.getLegacyKeys]）
     */
    suspend fun clearHistory(tab: SchoolCategoryTab) {
        dataStore.edit { prefs ->
            val keysList = SchoolHistoryModel.getLegacyKeys(tab) + SchoolHistoryModel.getPrimaryKeys(tab)
            keysList.forEach { keys ->
                prefs.remove(keys.id)
                prefs.remove(keys.name)
                prefs.remove(keys.folder)
            }
        }
    }
}

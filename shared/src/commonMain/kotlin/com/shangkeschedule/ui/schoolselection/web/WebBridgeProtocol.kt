package com.shangkeschedule.ui.schoolselection.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 全局 JSON 序列化配置
 */
val bridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * JS 与 Native 通信统一调用的消息外壳
 */
@Serializable
data class JsBridgeMessage(
    @SerialName("action") val action: String,
    @SerialName("callbackId") val callbackId: String? = null,
    @SerialName("payload") val payload: String? = null
)

// =========================================================================
// JS 接口 Payload 参数载荷定义
// =========================================================================

@Serializable
data class ShowToastPayload(
    val message: String
)

@Serializable
data class ShowAlertPayload(
    val titleText: String,
    val contentText: String,
    val confirmText: String? = null
)

@Serializable
data class ShowPromptPayload(
    val titleText: String,
    val tipText: String,
    val defaultText: String = "",
    val validatorJsFunction: String? = null
)

@Serializable
data class ShowSingleSelectionPayload(
    val titleText: String,
    val itemsJsonString: String,
    val defaultSelectedIndex: Int = -1
)

@Serializable
data class SaveCoursesPayload(
    val coursesJsonString: String
)

@Serializable
data class SaveConfigPayload(
    val configJsonString: String
)

@Serializable
data class SaveTimeSlotsPayload(
    val timeSlotsJsonString: String
)

/**
 * 「适配脚本回传抓取结果」的统一载荷（成绩 / 空教室 / 学业情况三种钩子共用）。
 *
 * [dataJsonString] 是钩子返回值的 JSON 串原文，由 Native 侧按用途各自的模型解析：
 * - 成绩：[ScannedGradePayload] 数组；
 * - 空教室：与 [EmptyClassroomRoom] 同形的对象数组；
 * - 学业：[ScannedStudyPayload] 对象。
 *
 * 为什么不在钩子侧就解析成强类型再逐个字段回传：三种用途的字段差异很大，
 * 且真正的模型定义在 Native（Kotlin）侧，保持「JS 只搬运 JSON、Native 负责解释」
 * 这条既有分工，接入新钩子时不必改协议。
 */
@Serializable
data class AdapterScanPayload(
    val dataJsonString: String = ""
)

/**
 * 适配脚本钩子「识别本页成绩」回传的单条成绩。
 *
 * 比通用脚本 [ScannedGrade] 多两个字段，因为**适配脚本知道学校自己的表结构**：
 * - [semester]：本校成绩页的学期（如「2024-2025-1」）。通用脚本一律落到「未标注学期」，
 *   适配脚本能把真实学期带回来，用户不必逐条改；
 * - [category]：课程性质（必修 / 选修 / 通识…），学业情况页按它分类统计学分，
 *   通用脚本只能读表里恰好有的「性质」列；
 * - [gradePoint]（v4.75.0）：**本校绩点**。这是各校按自己规则算出的既成事实
 *   （正方 V9 成绩接口的 `jd` 字段，如 "4.00"），本机换算表不可能对上；
 *   此前钩子不回传该字段，学校算出的绩点在抓取时被整列丢弃 —— 这正是
 *   「App 算出的绩点与学校对不上」的首要成因。
 */
@Serializable
data class ScannedGradePayload(
    val courseName: String = "",
    val credit: Double? = null,
    val scoreText: String = "",
    val semester: String? = null,
    val category: String? = null,
    val gradePoint: Double? = null
)

/**
 * 适配脚本钩子「识别学业情况」回传的培养方案信息（v4.75.0 起含课程清单）。
 *
 * 只有「要求」是学校侧的事实（培养方案规定的各类别应修学分 / 应修门数），
 * 「已获学分」「已修门数」永远由本机成绩表现算（见 `GradeRepository.computeStudyProgress`），
 * 因此钩子**不回传已修数据**——两份数据算学分必然打架。
 *
 * [courses] 是**可选**扩展：学校能把培养方案的课程清单抓出来时回传，
 * 于是「还没修 / 还没出成绩的课程」也能进本机统计。抓不到就留空
 * （页面会引导用户手填或粘贴导入），**不要为了凑数硬编码选择器**。
 */
@Serializable
data class ScannedStudyPayload(
    val requirements: List<ScannedStudyRequirementPayload> = emptyList(),
    val courses: List<ScannedCurriculumCoursePayload> = emptyList()
)

/** 单条培养方案学分 / 门数要求（v4.75.0 起含应修门数）。 */
@Serializable
data class ScannedStudyRequirementPayload(
    val category: String = "",
    val requiredCredits: Double = 0.0,
    /**
     * 该类别**应修门数**；null / <= 0 = 本校页面没给。
     * 正方 V9 学业情况页的类别行末尾带「共（N）门 通过（M）门」，N 即应修门数。
     */
    val requiredCourses: Int? = null
)

/**
 * 适配脚本钩子回传的一条培养方案课程（v4.75.0）。
 *
 * @param courseName 课程名；空白项会被丢弃。
 * @param category 课程类别，须与本机成绩的 `category` 对得上。
 * @param credit 该课程学分；null = 未知。
 * @param suggestedTerm 建议修读学期；null = 未知。
 */
@Serializable
data class ScannedCurriculumCoursePayload(
    val courseName: String = "",
    val category: String? = null,
    val credit: Double? = null,
    val suggestedTerm: String? = null
)

/**
 * 适配脚本错误上报。
 *
 * [kind] 取值：`adapter`（适配脚本自行抛出/Promise 拒绝）、`error`（window 未捕获异常）、
 * `unhandledrejection`（未处理的 Promise 拒绝）、`noEntry`（自动启动探测未找到入口）。
 * `noEntry` 的 [message] 只作为去重键，真正的用户文案由 Native 侧按语言给出。
 */
@Serializable
data class ReportErrorPayload(
    val kind: String = "",
    val message: String = "",
    val stack: String = ""
)

// =========================================================================
// Helper 工具函数
// =========================================================================

/**
 * 构造响应 JS 端 Promise 的执行脚本
 *
 * @param callbackId 消息唯一下发 ID
 * @param isSuccess 是否成功响应 (true: resolve, false: reject)
 * @param resultRawJs 原生 JS 字符串或 JSON 对象字面量
 */
fun buildJsCallbackScript(callbackId: String, isSuccess: Boolean, resultRawJs: String): String {
    // callbackId 来自网页，必须 JSON 转义后再拼进脚本，避免闭合引号注入任意 JS 片段
    val safeCallbackId = bridgeJson.encodeToString(callbackId)
    return "window._shangkeNativeCallback($safeCallbackId, $isSuccess, $resultRawJs);"
}

// =========================================================================
// JS 端抹平与挂载初始化脚本
// =========================================================================

val JS_BRIDGE_INIT = """
(function() {
    if (window._shangkeBridgeInjected) return;
    window._shangkeBridgeInjected = true;

    var callbacks = {};
    var callbackCounter = 0;

    /**
     * 动态获取当前可用的 Native 发送管道
     * 不在初始化时死板锁定，避免空网站/初始化极早期 _shangkeNativeBridge 还没准备好导致失效
     */
    function postRawMessage(msg) {
        if (window._shangkeNativeBridge && typeof window._shangkeNativeBridge.postMessage === 'function') {
            window._shangkeNativeBridge.postMessage(msg);
            return;
        }
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.shangkeBridge) {
            window.webkit.messageHandlers.shangkeBridge.postMessage(msg);
            return;
        }
        if (typeof window.cefQuery === 'function') {
            window.cefQuery({ request: msg });
            return;
        }
        console.warn("[ShangKeBridge] Native bridge unavailable:", msg);
    }

    /**
     * 通用底层管道：将请求统一转为 JSON 发送给 Native
     */
    function postMessageToNative(action, payload, callbackId) {
        // 记录脚本已经自行开始工作（与用户交互或写入数据）。
        // 自执行型脚本一旦触发过这些动作，说明它不需要宿主再补调用入口函数，
        // 避免重复导入，也避免误报"未找到导入入口"。
        if (action === 'saveImportedCourses' || action === 'saveCourseConfig' || action === 'savePresetTimeSlots' ||
            action === 'showAlert' || action === 'showPrompt' || action === 'showSingleSelection') {
            window.__shangkeImportTriggered = true;
        }

        var msg = JSON.stringify({
            action: action,
            callbackId: callbackId || null,
            payload: payload ? JSON.stringify(payload) : null
        });

        postRawMessage(msg);
    }

    /**
     * 适配脚本 / 页面脚本的全局兜底错误上报。
     *
     * 背景：195 个内置适配脚本里有 130 个含网络请求却全文没有 .catch，
     * 脚本一旦抛错就彻底静默——用户只看到"点了执行导入没反应"。
     * 这里把未捕获异常与未处理的 Promise 拒绝统一回传 Native，
     * 由 Native 侧给出本地化提示并复位导入运行状态。
     */
    var reportedErrorKeys = {};
    var reportedErrorCount = 0;
    window.__shangkeScriptErrorReported = false;

    function reportScriptError(kind, message, stack) {
        try {
            var text = String(message == null ? '' : message);
            var key = kind + '|' + text;
            // 同一段错误只报一次；页面自身脚本风暴式抛错时最多报 5 条，避免刷屏
            if (reportedErrorKeys[key]) return;
            if (reportedErrorCount >= 5) return;
            reportedErrorKeys[key] = true;
            reportedErrorCount++;
            window.__shangkeScriptErrorReported = true;
            postMessageToNative('reportAdapterError', {
                kind: kind,
                message: text,
                stack: String(stack == null ? '' : stack)
            });
        } catch (ignored) {
            // 上报失败时不再抛出，避免递归触发 error 事件
        }
    }

    // 适配脚本显式抛出的错误（含其返回 Promise 的拒绝）
    window.__shangkeReportScriptError = function(error) {
        reportScriptError('adapter', (error && error.message) || error, (error && error.stack) || '');
    };

    // 自动启动探测未找到入口：让 Native 侧复位「执行导入」并给出多语言提示
    window.__shangkeReportNoEntry = function(message) {
        reportScriptError('noEntry', message, '');
    };

    window.addEventListener('error', function(event) {
        // 过滤资源加载失败（img / script 404 之类），它们没有可读的错误信息
        if (!event || !event.message) return;
        var location = event.filename ? (event.filename + ':' + event.lineno) : '';
        reportScriptError('error', event.message, (event.error && event.error.stack) || location);
    });

    window.addEventListener('unhandledrejection', function(event) {
        var reason = event && event.reason;
        reportScriptError(
            'unhandledrejection',
            (reason && reason.message) || reason || 'Unhandled promise rejection',
            (reason && reason.stack) || ''
        );
    });

    /**
     * Native 异步逻辑完成后的响应全局入口
     */
    window._shangkeNativeCallback = function(callbackId, isSuccess, result) {
        var cb = callbacks[callbackId];
        if (cb) {
            if (isSuccess) {
                cb.resolve(result);
            } else {
                cb.reject(result);
            }
            delete callbacks[callbackId];
        }
    };

    // 1. 异步 Promise 调用的 JS 接口
    var shangkeBridgePromise = {
        showAlert: function(titleText, contentText, confirmText) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showAlert', {
                    titleText: titleText || '',
                    contentText: contentText || '',
                    confirmText: confirmText || null
                }, id);
            });
        },
        showPrompt: function(titleText, tipText, defaultText, validatorJsFunction) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showPrompt', {
                    titleText: titleText || '',
                    tipText: tipText || '',
                    defaultText: defaultText || '',
                    validatorJsFunction: validatorJsFunction || ''
                }, id);
            });
        },
        showSingleSelection: function(titleText, items, defaultSelectedIndex) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                var itemsJson = (typeof items === 'string') ? items : JSON.stringify(items || []);
                postMessageToNative('showSingleSelection', {
                    titleText: titleText || '',
                    itemsJsonString: itemsJson,
                    defaultSelectedIndex: defaultSelectedIndex !== undefined ? defaultSelectedIndex : -1
                }, id);
            });
        },
        saveImportedCourses: function(coursesJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveImportedCourses', { coursesJsonString: coursesJsonString }, id);
            });
        },
        saveCourseConfig: function(configJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveCourseConfig', { configJsonString: configJsonString }, id);
            });
        },
        savePresetTimeSlots: function(timeSlotsJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('savePresetTimeSlots', { timeSlotsJsonString: timeSlotsJsonString }, id);
            });
        }
    };

    /**
     * 导入会话内的适配脚本提示去重。
     *
     * 195 个内置适配脚本自己会弹「导入成功：N 条课程安排」「正在获取课表数据…」这类
     * 提示，与 Native 侧的结果提示、底部「正在执行导入脚本…」状态条语义完全重复；
     * 而 ToastManager 是 CONFLATED 语义（新消息直接覆盖旧消息），两条一起发只会
     * 互相顶掉，用户看到哪条取决于时序。这里统一静默「成功」「进行中」两类语义，
     * 把结果提示的话语权收到 Native 侧 —— 一处改动覆盖全部适配脚本。
     *
     * 错误类信息一律原样放行：脚本的报错（「未能获取课表数据…」「未找到课表表格」）
     * 比 Native 的通用文案更有信息量，静默掉反而让用户无从排查。
     */
    function shouldSuppressAdapterToast(message) {
        if (!window.__shangkeImportActive) return false;
        var text = String(message == null ? '' : message);
        if (!text) return false;
        // 先判错误语义：带失败/异常字样的提示绝不静默
        if (/失败|錯誤|错误|出错|出錯|未能|无法|無法|异常|異常|重试|重試/.test(text)) return false;
        // 成功类：导入/匯入 + 成功/完成，或 成功/完成 + 导入/匯入/添加/写入
        if (/(导入|匯入)/.test(text) && /(成功|完成)/.test(text)) return true;
        if (/(成功|完成)/.test(text) && /(导入|匯入|添加|新增|写入|寫入)/.test(text)) return true;
        // 配套数据的成功提示（作息时间 / 学期配置的保存、同步、设置）由课程落库那条统一代表
        if (/(成功|完成)/.test(text) && /(保存|儲存|同步|设置|設定|作息|学期|學期|时间段|時間段)/.test(text)) return true;
        // 进行中 / 启动类：与底部常驻状态条重复
        if (/正在|请稍候|請稍候|请等待|請等待|加载中|載入中|处理中|處理中|启动|啟動|开始|開始/.test(text)) return true;
        return false;
    }

    // 2. 单向/同步调用的 JS 接口
    var shangkeBridge = {
        showToast: function(message) {
            if (shouldSuppressAdapterToast(message)) return;
            postMessageToNative('showToast', { message: message });
        },
        notifyTaskCompletion: function() {
            // 会话结束：脚本后续自发提示（用户继续在教务页操作）恢复正常显示
            window.__shangkeImportActive = false;
            postMessageToNative('notifyTaskCompletion');
        }
    };

    // 3. 挂载全局对象
    window.shangkeBridgePromise = shangkeBridgePromise;
    window.shangkeBridge = shangkeBridge;

    // 4. 早期脚本兼容层
    // 一部分适配脚本（尤其是各大通用教务平台脚本）直接调用全局 Bridge / BRIDGE 对象，
    // 若不提供该对象，脚本会在首次调用时抛出 ReferenceError 而整体中断，表现为"点击导入无反应"。
    // 这里将旧命名统一映射到当前 Bridge 实现，使新旧脚本无需修改即可共存。
    function createLegacyBridge() {
        var taskFinished = false;

        function finishTask() {
            if (taskFinished) return;
            taskFinished = true;
            shangkeBridge.notifyTaskCompletion();
        }

        return {
            showToast: function(message) {
                shangkeBridge.showToast(message);
            },
            showAlert: function(titleText, contentText, confirmText) {
                window.__shangkeImportTriggered = true;
                return shangkeBridgePromise.showAlert(titleText, contentText, confirmText);
            },
            showPrompt: function(titleText, tipText, defaultText, validatorJsFunction) {
                window.__shangkeImportTriggered = true;
                return shangkeBridgePromise.showPrompt(titleText, tipText, defaultText, validatorJsFunction);
            },
            showSingleSelection: function(titleText, items, defaultSelectedIndex) {
                window.__shangkeImportTriggered = true;
                return shangkeBridgePromise.showSingleSelection(titleText, items, defaultSelectedIndex);
            },
            saveImportedCourses: function(coursesJsonString) {
                // 旧脚本不会主动通知任务完成，保存成功后由兼容层代为收尾，
                // 保证导入完成后能自动回到课表页面。
                return shangkeBridgePromise.saveImportedCourses(coursesJsonString).then(function(result) {
                    finishTask();
                    return result;
                });
            },
            saveCourseConfig: function(configJsonString) {
                return shangkeBridgePromise.saveCourseConfig(configJsonString);
            },
            savePresetTimeSlots: function(timeSlotsJsonString) {
                return shangkeBridgePromise.savePresetTimeSlots(timeSlotsJsonString);
            },
            notifyTaskCompletion: function() {
                finishTask();
            }
        };
    }

    var legacyBridge = createLegacyBridge();
    window.Bridge = legacyBridge;
    window.BRIDGE = legacyBridge;

    // 旧版兼容接口
    window.AndroidBridgePromise = shangkeBridgePromise;
    window.AndroidBridge = shangkeBridge;
})();
""".trimIndent()

/**
 * 适配脚本执行后的自动启动探测脚本。
 *
 * 早期适配脚本执行时往往只把抓取函数挂载到 window（例如 window.zhengfangImport），
 * 等待外部再次触发，导致用户点击"执行导入"后只弹出一句提示而没有实际导入动作。
 * 该脚本负责探测并调用这些入口函数，使一次点击即可完成导入。
 */
val JS_IMPORT_AUTOSTART = """
(function() {
    if (window.__shangkeImportTriggered) return;

    function notify(message) {
        if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
            window.shangkeBridge.showToast(message);
        }
    }

    function reportError(error) {
        // 优先走 Native 错误上报：Native 侧负责多语言文案并复位「执行导入」的运行状态；
        // 页面未注入新版 Bridge 时回退到原生 Toast。
        if (typeof window.__shangkeReportScriptError === 'function') {
            window.__shangkeReportScriptError(error);
        } else {
            notify('导入失败：' + (error && error.message ? error.message : error));
        }
    }

    var entry = null;

    // 1. 适配脚本显式声明的统一入口（最可靠，不依赖任何全局属性枚举行为）
    if (typeof window.shangkeImportEntry === 'function') {
        entry = window.shangkeImportEntry;
    }

    // 2. 约定入口名
    if (!entry) {
        var preferredNames = ['startImport', 'runImport', 'scheduleImport', 'importCourses', 'importSchedule'];
        for (var p = 0; p < preferredNames.length && !entry; p++) {
            if (typeof window[preferredNames[p]] === 'function') {
                entry = window[preferredNames[p]];
            }
        }
    }

    // 3. 兜底：扫描适配脚本本次新挂载到 window 上的 *Import 函数
    if (!entry) {
        var beforeKeys = window.__shangkeWindowKeysBefore || [];
        var seen = {};
        for (var i = 0; i < beforeKeys.length; i++) {
            seen[beforeKeys[i]] = true;
        }

        try {
            var currentKeys = Object.keys(window);
            for (var n = 0; n < currentKeys.length; n++) {
                var key = currentKeys[n];
                if (seen[key]) continue;
                if (/(^|[a-z0-9])[Ii]mport${'$'}/.test(key) && typeof window[key] === 'function') {
                    entry = window[key];
                    break;
                }
            }
        } catch (e) {
            // 部分教务页面对 window 枚举有限制，忽略即可
        }
    }

    if (!entry) {
        // 自启动型适配器无法在此刻被识别：它们在顶层直接调用自己的入口函数（如 runImportFlow），
        // 入口名不符合上述探测规则，但流程其实已经跑起来了；要等异步流程走到桥接回调
        // （showToast/showAlert/saveImportedCourses 等）才会置位 __shangkeImportTriggered。
        // 因此这里不立即报错，而是给一个宽限期后再判定，避免误报"未找到导入入口"。
        // 注意：切勿改为"扩大入口名单后直接调用"——那会让已自启动的适配器并发跑两遍。
        setTimeout(function () {
            if (window.__shangkeImportTriggered) return; // 适配器已自行启动，静默
            if (window.__shangkeScriptErrorReported) return; // 脚本已经报过错，不重复提示
            // 导入实际没跑起来：结束会话态，否则之后教务页自身的提示会被静默
            window.__shangkeImportActive = false;
            var noEntryMessage = '未找到导入入口，请确认已打开课表页面后重试，或改用文本导入。';
            if (typeof window.__shangkeReportNoEntry === 'function') {
                // 交给 Native：既能复位「执行导入」按钮，又能按当前语言给出提示
                window.__shangkeReportNoEntry(noEntryMessage);
            } else {
                notify(noEntryMessage);
            }
        }, 1500);
        return;
    }

    try {
        var result = entry();
        if (result && typeof result.catch === 'function') {
            result.catch(reportError);
        }
    } catch (e) {
        reportError(e);
    }
})();
""".trimIndent()

/**
 * 「一键导航到课表」注入脚本。
 *
 * 在教务系统网页内寻找课表入口并跳转，供底部栏按钮调用。
 * 优先级：适配脚本显式声明的 `window.shangkeNavigateToTimetable()` → DOM 文本探测。
 * 探测时优先匹配更长、更精确的菜单文案（课表查询 / 学生课表 / 课表…），
 * 命中后点击其最近的 `a` / `button` / `[onclick]` 祖先节点。
 *
 * 返回值（供 evaluateJavascript 回调解析）：`found` 命中并已触发跳转，`notfound` 未找到入口。
 */
val JS_NAVIGATE_TO_TIMETABLE = """
(function() {
    try {
        // 1. 适配脚本显式声明的跳转入口（最可靠）
        if (typeof window.shangkeNavigateToTimetable === 'function') {
            try {
                window.shangkeNavigateToTimetable();
                return 'found';
            } catch (e) {
                // 入口抛错时回落到 DOM 探测
            }
        }

        // 2. DOM 文本探测：长关键词优先级更高
        var keywords = ['课表查询', '学生课表', '我的课表', '理论课表', '班级课表', '课表信息', '课程表', '课表', 'timetable', 'schedule'];

        function isVisible(el) {
            if (!el || typeof el.getBoundingClientRect !== 'function') return false;
            var rect = el.getBoundingClientRect();
            if (rect.width < 2 || rect.height < 2) return false;
            if (typeof window.getComputedStyle === 'function') {
                var style = window.getComputedStyle(el);
                if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
            }
            return true;
        }

        var candidates = document.querySelectorAll('a, button, [onclick], li, td');
        var best = null;
        var bestScore = -1;

        for (var i = 0; i < candidates.length; i++) {
            var el = candidates[i];
            if (!isVisible(el)) continue;
            var text = (el.textContent || '').replace(/\s+/g, '').trim();
            if (!text || text.length > 24) continue;
            var hay = text.toLowerCase();
            for (var k = 0; k < keywords.length; k++) {
                var needle = keywords[k].toLowerCase();
                if (hay.indexOf(needle) === -1) continue;
                // 关键词越长越精确；同分时元素文本越短越像一个独立入口
                var score = needle.length * 100 - text.length;
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
        }

        if (!best) return 'notfound';

        var trigger = best;
        if (typeof best.closest === 'function') {
            trigger = best.closest('a, button, [onclick]') || best;
        }
        if (trigger.tagName === 'A' && trigger.href) {
            trigger.target = '_self';
        }
        trigger.click();
        return 'found';
    } catch (e) {
        return 'notfound';
    }
})();
""".trimIndent()

/**
 * 组装最终注入 WebView 的导入脚本。
 *
 * @param tableId 导入的目标课表 ID
 * @param adapterJsCode 适配脚本源码
 */
fun buildImportScript(tableId: String, adapterJsCode: String): String {
    val safeTableId = bridgeJson.encodeToString(tableId)
    return """
    window.currentTableId = $safeTableId;
    window.__shangkeImportTriggered = false;
    // 导入会话开始：期间的「成功」「进行中」类适配脚本提示由 shouldSuppressAdapterToast 静默
    window.__shangkeImportActive = true;
    window.__shangkeWindowKeysBefore = Object.keys(window);
    $adapterJsCode
    ;$JS_IMPORT_AUTOSTART
    """.trimIndent()
}

/**
 * 「识别本页成绩」注入脚本（通用成绩表解析，不依赖适配脚本）。
 *
 * 面向教务系统成绩查询页：优先按表头映射列（课程名 / 学分 / 成绩 / 性质），
 * 表头识别失败时退回逐行启发式（取最右侧「像成绩」的单元格为成绩，
 * 其左侧 0–30 的数字为学分，首个非数字单元格为课程名）。
 *
 * 返回值经 Base64 编码，规避 `evaluateJavascript` 回调的 JSON 转义问题：
 * 空字符串 = 未识别到任何成绩；否则是 JSON 数组（UTF-8 → Base64），元素形如
 * `{"courseName":"高等数学","credit":5,"scoreText":"92","category":"必修"}`。
 *
 * 设计取舍：不做「一键进成绩页」的自动导航（各校入口差异太大），
 * 由用户在页面内自行导航后点「识别本页成绩」，识别失败可直接退回手动/粘贴录入。
 */
val JS_SCAN_GRADES = """
(function() {
    try {
        function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }

        function toBase64(str) {
            var bytes = new TextEncoder().encode(str);
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var out = '';
            for (var i = 0; i < bytes.length; i += 3) {
                var b0 = bytes[i];
                var b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
                var b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
                out += chars[b0 >> 2];
                out += chars[((b0 & 3) << 4) | (b1 >> 4)];
                out += i + 1 < bytes.length ? chars[((b1 & 15) << 2) | (b2 >> 6)] : '=';
                out += i + 2 < bytes.length ? chars[b2 & 63] : '=';
            }
            return out;
        }

        var LEVELS = ['优秀', '良好', '中等', '及格', '不及格', '合格', '不合格', '通过', '不通过', '优', '良', '中'];
        function isLevel(text) {
            for (var i = 0; i < LEVELS.length; i++) if (text === LEVELS[i]) return true;
            return /^[A-Fa-f][+-]?$/.test(text);
        }
        function scoreOf(text) {
            if (!text) return null;
            if (isLevel(text)) return text;
            var m = text.match(/^(\d{1,3}(\.\d+)?)\s*分?$/);
            if (!m) return null;
            var v = parseFloat(m[1]);
            return v >= 0 && v <= 100 ? text : null;
        }
        function creditOf(text) {
            if (!text) return null;
            var m = String(text).match(/\d{1,2}(\.\d+)?/);
            if (!m) return null;
            var v = parseFloat(m[0]);
            return v > 0 && v <= 30 ? v : null;
        }

        var NAME_RE = /课程名称|课程名|科目名称|教学班名称|科目|课程/;
        var CREDIT_RE = /学分/;
        // v4.75.0：SCORE_RE **刻意不含「绩点」**。此前它含「绩点」，而表头识别取的是
        // 「第一个命中列」，于是正方等把「绩点」列排在「成绩」列之前的成绩表，
        // 会把 3.7 这样的绩点当成成绩导入（scoreOf 接受 0–100 的任意数字），
        // 再被按百分制换算一次，绩点与平均分全错且极难排查。
        var SCORE_RE = /成绩|分数|总评|得分/;
        // 单独的绩点列：命中即作为「本校绩点」回传，而不是拿去换算。
        var GPA_RE = /绩点|平均绩点|课程绩点|GPA/;
        var CATEGORY_RE = /性质|类别|修读方式|课程类型|课程属性|课程归属|修读性质|课程分组|课程种类|课程模块/;

        function cellsOf(row) {
            var nodes = row.querySelectorAll('td, th, [role="gridcell"], [role="cell"]');
            var out = [];
            for (var i = 0; i < nodes.length; i++) out.push(norm(nodes[i].textContent));
            return out;
        }
        function firstIndex(list, re) {
            for (var i = 0; i < list.length; i++) if (re.test(list[i])) return i;
            return -1;
        }
        // 命中 re 但**不**命中 excludeRe 的第一个下标：用于把「绩点」列与「成绩」列分开。
        // 混写表头（如「成绩绩点」）会被两者同时命中，此时让 SCORE 优先，避免同一列当两个用途。
        function firstIndexExcluding(list, re, excludeRe) {
            for (var i = 0; i < list.length; i++) {
                if (re.test(list[i]) && !(excludeRe && excludeRe.test(list[i]))) return i;
            }
            return -1;
        }

        var rows = [];
        var candidates = document.querySelectorAll('table tr, [role="row"], .el-table__row, .ant-table-row, .ivu-table-row');
        for (var i = 0; i < candidates.length; i++) {
            var cells = cellsOf(candidates[i]);
            if (cells.length >= 2) rows.push(cells);
        }

        var results = [];
        var seen = {};
        // 绩点格 → 数值；非 0–5 的数字（含空串、'-'、'--'）一律当作「本校没给」。
        // 不猜、不填 0：0 会被当成「挂科绩点」污染汇总。
        function gradePointOf(text) {
            if (text === null || text === undefined) return null;
            var m = String(text).trim().match(/^(\d{1,2}(?:\.\d+)?)$/);
            if (!m) return null;
            var v = parseFloat(m[1]);
            return (v >= 0 && v <= 5) ? v : null;
        }
        function push(name, credit, score, category, gradePoint) {
            if (!name || !score) return;
            if (name.length > 60) return;
            if (scoreOf(score) === null) return;
            var key = name + '|' + score;
            if (seen[key]) return;
            seen[key] = true;
            results.push({
                courseName: name,
                credit: credit,
                scoreText: score,
                category: category,
                gradePoint: gradePointOf(gradePoint)
            });
        }

        // 1. 表头映射（最可靠）
        var headerIndex = -1;
        var header = null;
        for (var r = 0; r < rows.length; r++) {
            var h = rows[r];
            var nameIdx = firstIndex(h, NAME_RE);
            var creditIdx = firstIndex(h, CREDIT_RE);
            var scoreIdx = firstIndexExcluding(h, SCORE_RE, GPA_RE);
            if (nameIdx >= 0 && (creditIdx >= 0 || scoreIdx >= 0)) {
                headerIndex = r;
                header = {
                    name: nameIdx,
                    credit: creditIdx,
                    score: scoreIdx,
                    category: firstIndex(h, CATEGORY_RE),
                    gpa: firstIndexExcluding(h, GPA_RE, SCORE_RE)
                };
                break;
            }
        }
        if (header) {
            for (var r2 = headerIndex + 1; r2 < rows.length; r2++) {
                var row = rows[r2];
                if (row.length <= Math.max(header.name, header.score)) continue;
                var name = row[header.name];
                if (!name || NAME_RE.test(name)) continue;
                var score = header.score >= 0 ? row[header.score] : '';
                if (scoreOf(score) === null) continue;
                var credit = header.credit >= 0 ? creditOf(row[header.credit]) : null;
                var category = header.category >= 0 ? row[header.category] : null;
                var gpa = header.gpa >= 0 ? row[header.gpa] : null;
                push(name, credit, score, category && category.length <= 20 ? category : null, gpa);
            }
        }

        // 2. 逐行启发式兜底（表头识别失败，或表头识别到但没抓到数据行）
        //    分两趟：先只认「整数或 ≥50」的分数（等级词不受限），避开行末的绩点列（3.7 / 4.0）；
        //    一趟都没命中才放宽到任意 1–100 的数，以免把真实的 48.5 分丢掉。
        function isStrictScore(text) {
            if (!text || scoreOf(text) === null) return false;
            if (isLevel(text)) return true;
            var v = parseFloat(text);
            if (!isFinite(v)) return false;
            return text.indexOf('.') < 0 || v >= 50;
        }
        function scanRowsHeuristically(strict) {
            for (var r3 = 0; r3 < rows.length; r3++) {
                var cells3 = rows[r3];
                if (cells3.length < 2) continue;
                var scoreIdx3 = -1;
                for (var c = cells3.length - 1; c >= 0; c--) {
                    if (scoreOf(cells3[c]) === null || NAME_RE.test(cells3[c])) continue;
                    if (strict && !isStrictScore(cells3[c])) continue;
                    scoreIdx3 = c;
                    break;
                }
                if (scoreIdx3 <= 0) continue;
                var nameIdx3 = -1;
                for (var c2 = 0; c2 < scoreIdx3; c2++) {
                    var t = cells3[c2];
                    if (!t || t.length < 2) continue;
                    if (/^\d+(\.\d+)?$/.test(t)) continue;
                    if (SCORE_RE.test(t)) continue;
                    nameIdx3 = c2;
                    break;
                }
                if (nameIdx3 < 0) continue;
                var credit3 = null;
                for (var c3 = scoreIdx3 - 1; c3 > nameIdx3; c3--) {
                    var cv = creditOf(cells3[c3]);
                    if (cv !== null) { credit3 = cv; break; }
                }
                // 启发式路径无法可靠区分「哪一列是绩点」，故不猜绩点（宁可留空）
                push(cells3[nameIdx3], credit3, cells3[scoreIdx3], null, null);
            }
        }
        if (results.length === 0) scanRowsHeuristically(true);
        if (results.length === 0) scanRowsHeuristically(false);

        if (!results.length) return '';
        return toBase64(JSON.stringify(results));
    } catch (e) {
        return '';
    }
})();
""".trimIndent()

/**
 * 适配脚本钩子名称：由适配脚本自行声明，返回本校的成绩 / 空教室 / 学业情况。
 *
 * 命名与既有的 `window.shangkeNavigateToTimetable` / `shangkeNavigateToEmptyClassroom`
 * 保持一致（`shangke` 前缀 + 动词短语），避免与教务页自身的全局函数撞名。
 */
object AdapterHooks {
    /** 识别本页成绩：`function () => Promise|Array<ScannedGradePayload>`。 */
    const val SCAN_GRADES = "shangkeScanGrades"

    /** 读取本页空教室：`function () => Promise|Array<EmptyClassroomRoom>`。 */
    const val SCAN_EMPTY_CLASSROOMS = "shangkeScanEmptyClassrooms"

    /** 识别学业情况（培养方案学分要求）：`function () => Promise|ScannedStudyPayload`。 */
    const val SCAN_STUDY = "shangkeScanStudy"
}

/** 三个钩子各自对应的 JS→Native 投递动作名（与 [WebBridgeHandler.onMessageReceived] 对齐）。 */
object AdapterScanActions {
    const val GRADES = "deliverAdapterGrades"
    const val EMPTY_CLASSROOMS = "deliverAdapterEmptyClassrooms"
    const val STUDY = "deliverAdapterStudy"
}

/**
 * 探测适配脚本是否声明了某个钩子，返回 `'present'` / `'missing'`。
 *
 * 为什么要先探测而不是直接注入调用：钩子是**可选**的（195 个适配脚本里绝大多数没有），
 * 直接调用会在页面上抛 `undefined is not a function`，而这类异常会被
 * `reportScriptError` 捕获并弹「适配脚本出错」，把「本校没适配」误报成「脚本坏了」。
 *
 * @param adapterJsCode 适配脚本源码；探测时一并注入，注入是幂等的（脚本自带 IIFE 守卫）。
 * @param hookName [AdapterHooks] 中的钩子名。
 */
fun buildAdapterHookProbeScript(adapterJsCode: String, hookName: String): String {
    val safeHook = bridgeJson.encodeToString(hookName)
    return """
    $adapterJsCode
    ;(function () {
        try {
            return typeof window[$safeHook] === 'function' ? 'present' : 'missing';
        } catch (e) {
            return 'missing';
        }
    })();
    """.trimIndent()
}

/**
 * 组装「调用适配脚本钩子并回传结果」的注入脚本。
 *
 * 钩子允许是异步的（适配脚本多半要 `fetch` 本校接口），因此这里统一用
 * `Promise.resolve(...)` 包一层：同步返回值与 Promise 走同一条路径。
 * 结果经 [AdapterScanActions] 对应的动作回传 Native —— 复用既有的桥接管道，
 * 而不是让 Native 侧轮询全局变量或依赖 `evaluateJavascript` 的同步返回值
 * （后者对 Promise 只会拿到 `{}`，是这条链路最容易踩的坑）。
 *
 * 钩子抛错 / Promise 拒绝时回传一条 `reportAdapterError`，由 Native 侧
 * 按既有逻辑提示用户并回落到通用脚本。
 *
 * @param adapterJsCode 适配脚本源码。
 * @param hookName [AdapterHooks] 中的钩子名。
 * @param action [AdapterScanActions] 中对应的投递动作名。
 */
fun buildAdapterHookInvokeScript(
    adapterJsCode: String,
    hookName: String,
    action: String
): String {
    val safeHook = bridgeJson.encodeToString(hookName)
    val safeAction = bridgeJson.encodeToString(action)
    return """
    $adapterJsCode
    ;(function () {
        function post(action, payload) {
            try {
                var msg = JSON.stringify({
                    action: action,
                    callbackId: null,
                    payload: JSON.stringify(payload)
                });
                if (window._shangkeNativeBridge && typeof window._shangkeNativeBridge.postMessage === 'function') {
                    window._shangkeNativeBridge.postMessage(msg);
                }
            } catch (e) {
            }
        }
        function toBase64(str) {
            var bytes = new TextEncoder().encode(str);
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var out = '';
            for (var i = 0; i < bytes.length; i += 3) {
                var b0 = bytes[i];
                var b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
                var b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
                out += chars[b0 >> 2];
                out += chars[((b0 & 3) << 4) | (b1 >> 4)];
                out += i + 1 < bytes.length ? chars[((b1 & 15) << 2) | (b2 >> 6)] : '=';
                out += i + 2 < bytes.length ? chars[b2 & 63] : '=';
            }
            return out;
        }
        try {
            Promise.resolve(window[$safeHook]())
                .then(function (result) { post($safeAction, { dataJsonString: toBase64(JSON.stringify(result === undefined ? null : result)) }); })
                .catch(function (error) { post('reportAdapterError', { kind: 'adapter', message: String(error && error.message ? error.message : error), stack: '' }); });
        } catch (e) {
            post('reportAdapterError', { kind: 'adapter', message: String(e && e.message ? e.message : e), stack: '' });
        }
    })();
    """.trimIndent()
}

/**
 * 「定位空教室查询页」注入脚本。
 *
 * 与 [JS_NAVIGATE_TO_TIMETABLE] 同一套策略：适配脚本可显式声明
 * `window.shangkeNavigateToEmptyClassroom()`；否则在 DOM 里按菜单文案打分挑一个入口点击。
 *
 * 空教室入口的文案比课表更杂（空教室 / 空闲教室 / 教室查询 / 自习室 / 教室借用…），
 * 因此关键词表按「越精确越优先」排序，沿用长度加权打分，避免选中「教室」这种
 * 会命中一大片菜单的短词。找不到入口时返回 `notfound`，由 Native 侧提示用户
 * 自行在地址栏进入空教室页后直接点「读取本页空教室」。
 */
val JS_NAVIGATE_TO_EMPTY_CLASSROOM = """
(function() {
    try {
        if (typeof window.shangkeNavigateToEmptyClassroom === 'function') {
            try {
                window.shangkeNavigateToEmptyClassroom();
                return 'found';
            } catch (e) {
            }
        }

        var keywords = ['空教室查询', '空闲教室查询', '空教室', '空闲教室', '教室查询', '空闲时段查询', '自习教室', '自习室', '教室占用查询', '教室借用查询', '教室借用'];

        function isVisible(el) {
            if (!el || typeof el.getBoundingClientRect !== 'function') return false;
            var rect = el.getBoundingClientRect();
            if (rect.width < 2 || rect.height < 2) return false;
            if (typeof window.getComputedStyle === 'function') {
                var style = window.getComputedStyle(el);
                if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
            }
            return true;
        }

        var candidates = document.querySelectorAll('a, button, [onclick], li, td');
        var best = null;
        var bestScore = -1;

        for (var i = 0; i < candidates.length; i++) {
            var el = candidates[i];
            if (!isVisible(el)) continue;
            var text = (el.textContent || '').replace(/\s+/g, '').trim();
            if (!text || text.length > 24) continue;
            var hay = text.toLowerCase();
            for (var k = 0; k < keywords.length; k++) {
                var needle = keywords[k].toLowerCase();
                if (hay.indexOf(needle) === -1) continue;
                var score = needle.length * 100 - text.length;
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
        }

        if (!best) return 'notfound';

        var trigger = best;
        if (typeof best.closest === 'function') {
            trigger = best.closest('a, button, [onclick]') || best;
        }
        if (trigger.tagName === 'A' && trigger.href) {
            trigger.target = '_self';
        }
        trigger.click();
        return 'found';
    } catch (e) {
        return 'notfound';
    }
})();
""".trimIndent()

/**
 * 「定位学业情况查询页」注入脚本（v4.75.3）。
 *
 * ## 为什么必须有这个脚本
 *
 * App 打开教务页用的是学校配置里的 `import_url`（**首页 / 注册地址**），不是学业情况页。
 * 实测南通大学：首页 `index_initMenu.html` 里 `p.title1` 命中 **0 个**、「要求学分」**0 处**；
 * 而学业情况页 `xsxyqk_cxXsxyqkIndex.html` 命中 **14 个**、可解析出 **7 条**要求。
 * ⇒ 用户停在首页点「读取」，钩子必然读到 0 条 —— 症状是「导入不进去」，而��是脚本坏了。
 *
 * 课表与空教室都有各自的定位脚本（[JS_NAVIGATE_TO_TIMETABLE] / [JS_NAVIGATE_TO_EMPTY_CLASSROOM]），
 * **学业情况此前没有**，于是成了唯一「必须用户自己先点对菜单」的入口。
 *
 * ## 与空教室版本的差异（关键）
 *
 * 正方 V9 的菜单是 **Bootstrap 折叠下拉**：菜单项 `li` 本身 `display:list-item`、
 * `visibility:visible`，但**祖先 `ul.dropdown-menu` 是 `display:none`**，导致
 * `getBoundingClientRect()` 返回 0×0。直接沿用空教室那版的 `isVisible()` 会
 * **一个候选都选不出来**（实测命中 0）。
 * 这里在打分前先把隐藏的祖先逐层改成 `display:block`（实测只需展开 1 层，
 * 目标即从 0×0 变为 158×23）。
 *
 * ## 为什么必须先判断「是不是已经在目标页」
 *
 * 实测（2026-10-08，用户真实登录会话）：已经停在学业情况页时，本脚本**仍会选中
 * 「学生学业情况查询」菜单并点击**——即把用户从正确页面又点走，重新触发一轮加载。
 * 症状是「读着读着页面自己动了 / 一直转」，比不点更糟。
 * 所以开头先看页面上有没有培养方案数据，有就直接返回 `here`，不做任何点击。
 *
 * 返回 `here` / `found` / `notfound`：
 * - `here` —— 已在目标页，调用方直接调钩子，不要再点任何东西；
 * - `found` —— 已点菜单入口，等页面加载完再读；
 * - `notfound` —— 找不到入口，由 Native 侧提示用户自行进入该页。
 */
val JS_NAVIGATE_TO_STUDY = """
(function() {
    try {
        // 已在学业情况页：页面上有培养方案要求学分。不要再点菜单把自己点走。
        if (document.body && document.body.innerHTML.indexOf('要求学分') >= 0) {
            return 'here';
        }

        if (typeof window.shangkeNavigateToStudy === 'function') {
            try {
                window.shangkeNavigateToStudy();
                return 'found';
            } catch (e) {
            }
        }

        // 越精确越优先（长度加权打分，见下方 score 计算）
        var keywords = [
            '学生学业情况查询', '学业情况查询', '培养方案查询', '学分要求查询',
            '学业情况', '培养方案', '学分统计', '学业查询', '学分查询', '毕业审核'
        ];

        function isVisible(el) {
            if (!el || typeof el.getBoundingClientRect !== 'function') return false;
            var rect = el.getBoundingClientRect();
            return rect.width >= 2 && rect.height >= 2;
        }

        // 正方菜单常藏在 display:none 的下拉里，先展开隐藏祖先，否则全部被判不可见
        function revealHiddenAncestors(el) {
            var p = el.parentElement;
            while (p && p !== document.body) {
                var cs = null;
                try { cs = window.getComputedStyle(p); } catch (e) { cs = null; }
                if (cs && cs.display === 'none') {
                    try { p.style.display = 'block'; } catch (e) { }
                }
                p = p.parentElement;
            }
        }

        var candidates = document.querySelectorAll('a, button, [onclick], li, td, span');
        var best = null;
        var bestScore = -1;

        for (var i = 0; i < candidates.length; i++) {
            var el = candidates[i];
            revealHiddenAncestors(el);
            var text = (el.textContent || '').replace(/\s+/g, '').trim();
            if (!text || text.length > 24) continue;
            // 必须自己能被点开：内层 <a onclick="clickMenu(...)"> 才带跳转，
            // 外层 <li> 的 onclick 常常是空的（实测正是如此）
            var clickable = (el.tagName === 'A' || el.tagName === 'BUTTON' ||
                             (el.getAttribute && el.getAttribute('onclick')));
            if (!clickable) continue;
            if (!isVisible(el)) continue;
            var hay = text.toLowerCase();
            for (var k = 0; k < keywords.length; k++) {
                var needle = keywords[k].toLowerCase();
                if (hay.indexOf(needle) === -1) continue;
                var score = needle.length * 100 - text.length;
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
        }

        if (!best) return 'notfound';

        var trigger = best;
        if (typeof best.closest === 'function') {
            trigger = best.closest('a, button, [onclick]') || best;
        }
        if (trigger.tagName === 'A' && trigger.href) {
            trigger.target = '_self';
        }
        trigger.click();
        return 'found';
    } catch (e) {
        return 'notfound';
    }
})();
""".trimIndent()

/**
 * 「读取本页空教室」注入脚本（通用空教室结果表解析，不依赖适配脚本）。
 *
 * 与 [JS_SCAN_GRADES] 同构：先按表头映射列（教室 / 座位数 / 空闲节次 / 教学楼 / 校区），
 * 表头识别失败或识别到表头却没抓到数据行时，退回逐行启发式（第一个「像教室名」的
 * 单元格为教室，纯数字单元格为座位数，带「节」的单元格为空闲节次）。
 *
 * 为什么不做「按条件查空教室」（学期 / 校区 / 楼 / 周次 / 节次）：这些查询接口是
 * 各校教务自行实现的（正方 / 强智 / 金智的请求参数完全不同，且多数还要带会话令牌），
 * 在客户端硬编码参数拼装极易失效。这里改为让用户在真实的教务页里自己设条件点查询
 * ——页面本身就是最可靠的查询表单——应用只负责把结果表读成干净文本。
 *
 * 返回值经 Base64 编码（理由同 [JS_SCAN_GRADES]）：空字符串 = 未识别到任何空教室；
 * 否则是 JSON 数组，元素形如
 * `{"room":"教三305","campus":"","building":"三教","capacity":60,"freeSlots":"1-2节"}`。
 */
val JS_SCAN_EMPTY_CLASSROOMS = """
(function() {
    try {
        function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }

        function toBase64(str) {
            var bytes = new TextEncoder().encode(str);
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var out = '';
            for (var i = 0; i < bytes.length; i += 3) {
                var b0 = bytes[i];
                var b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
                var b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
                out += chars[b0 >> 2];
                out += chars[((b0 & 3) << 4) | (b1 >> 4)];
                out += i + 1 < bytes.length ? chars[((b1 & 15) << 2) | (b2 >> 6)] : '=';
                out += i + 2 < bytes.length ? chars[b2 & 63] : '=';
            }
            return out;
        }

        // 表头关键词
        var ROOM_RE = /教室|房间|房号|场地|机房|实验室|语音室|多媒体/;
        var CAPACITY_RE = /座位|容量|可容纳|容纳人数|人数/;
        var BUILDING_RE = /教学楼|楼栋|建筑|楼|馆/;
        var CAMPUS_RE = /校区/;
        var SLOT_RE = /节次|空闲|可用|时间段|时段|节/;

        function cellsOf(row) {
            var nodes = row.querySelectorAll('td, th, [role="gridcell"], [role="cell"]');
            var out = [];
            for (var i = 0; i < nodes.length; i++) out.push(norm(nodes[i].textContent));
            return out;
        }
        function firstIndex(list, re) {
            for (var i = 0; i < list.length; i++) if (re.test(list[i])) return i;
            return -1;
        }
        // 座位数只认「纯数字（可带 人/座）」的单元格，避免把「1-2节」里的 1 当成座位数
        function capacityOf(text) {
            var t = norm(text);
            if (!/^\d{1,4}\s*(人|座|个)?$/.test(t)) return null;
            var v = parseInt(t, 10);
            if (isNaN(v) || v <= 0 || v > 2000) return null;
            return v;
        }
        // 表头单元格自身的文案（如「教室」）不是数据，清掉
        function valueOf(text, headerRe) {
            var t = norm(text);
            if (!t || t.length > 40) return '';
            if (headerRe && headerRe.test(t)) return '';
            return t;
        }
        function looksLikeRoom(text) {
            var t = norm(text);
            if (!t || t.length > 40) return false;
            if (/^\d{1,4}$/.test(t)) return false;
            if (SLOT_RE.test(t) && !ROOM_RE.test(t)) return false;
            return ROOM_RE.test(t) || /^[A-Za-z]?[A-Za-z]?[-－]?\d{2,4}$/.test(t);
        }

        var rows = [];
        var candidates = document.querySelectorAll('table tr, [role="row"], .el-table__row, .ant-table-row, .ivu-table-row');
        for (var i = 0; i < candidates.length; i++) {
            var cells = cellsOf(candidates[i]);
            if (cells.length >= 2) rows.push(cells);
        }

        var results = [];
        var seen = {};
        function push(room, campus, building, capacity, freeSlots) {
            if (!room) return;
            if (room.length > 40) return;
            var key = room + '|' + (freeSlots || '');
            if (seen[key]) return;
            seen[key] = true;
            results.push({
                room: room,
                campus: campus || '',
                building: building || '',
                capacity: capacity,
                freeSlots: freeSlots || ''
            });
        }

        // 1. 表头映射（最可靠）
        var headerIndex = -1;
        var header = null;
        for (var r = 0; r < rows.length; r++) {
            var h = rows[r];
            var roomIdx = firstIndex(h, ROOM_RE);
            var capIdx = firstIndex(h, CAPACITY_RE);
            var slotIdx = firstIndex(h, SLOT_RE);
            if (roomIdx >= 0 && (capIdx >= 0 || slotIdx >= 0)) {
                headerIndex = r;
                header = {
                    room: roomIdx,
                    cap: capIdx,
                    slot: slotIdx,
                    building: firstIndex(h, BUILDING_RE),
                    campus: firstIndex(h, CAMPUS_RE)
                };
                break;
            }
        }
        if (header) {
            for (var r2 = headerIndex + 1; r2 < rows.length; r2++) {
                var row = rows[r2];
                if (row.length <= header.room) continue;
                var room = valueOf(row[header.room], ROOM_RE);
                if (!room || !looksLikeRoom(room)) continue;
                var cap = header.cap >= 0 ? capacityOf(row[header.cap]) : null;
                var slot = header.slot >= 0 ? valueOf(row[header.slot], SLOT_RE) : '';
                if (slot.length > 60) slot = '';
                // 至少要有一个「能用」的信号（座位数或空闲时段），否则多半是别的表
                if (cap === null && !slot) continue;
                push(
                    room,
                    header.campus >= 0 ? valueOf(row[header.campus], CAMPUS_RE) : '',
                    header.building >= 0 ? valueOf(row[header.building], BUILDING_RE) : '',
                    cap,
                    slot
                );
            }
        }

        // 2. 逐行启发式兜底（表头识别失败，或识别到表头但没抓到数据行）
        if (results.length === 0) {
            for (var r3 = 0; r3 < rows.length; r3++) {
                var cells3 = rows[r3];
                if (cells3.length < 2) continue;
                var roomIdx3 = -1;
                for (var c = 0; c < cells3.length; c++) {
                    if (looksLikeRoom(cells3[c])) {
                        roomIdx3 = c;
                        break;
                    }
                }
                if (roomIdx3 < 0) continue;
                var cap3 = null;
                var slot3 = '';
                for (var c2 = 0; c2 < cells3.length; c2++) {
                    if (c2 === roomIdx3) continue;
                    var t2 = cells3[c2];
                    if (!t2) continue;
                    if (cap3 === null) {
                        var cv = capacityOf(t2);
                        if (cv !== null) { cap3 = cv; continue; }
                    }
                    if (!slot3 && SLOT_RE.test(t2) && !CAPACITY_RE.test(t2) && t2.length <= 60) {
                        slot3 = t2;
                    }
                }
                if (cap3 === null && !slot3) continue;
                push(cells3[roomIdx3], '', '', cap3, slot3);
            }
        }

        if (!results.length) return '';
        return toBase64(JSON.stringify(results));
    } catch (e) {
        return '';
    }
})();
""".trimIndent()
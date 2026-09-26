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
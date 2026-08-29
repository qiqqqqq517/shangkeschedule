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

    // 2. 单向/同步调用的 JS 接口
    var shangkeBridge = {
        showToast: function(message) {
            postMessageToNative('showToast', { message: message });
        },
        notifyTaskCompletion: function() {
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
        notify('导入失败：' + (error && error.message ? error.message : error));
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
        notify('未找到导入入口，请确认已打开课表页面后重试，或改用文本导入。');
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
    window.__shangkeWindowKeysBefore = Object.keys(window);
    $adapterJsCode
    ;$JS_IMPORT_AUTOSTART
    """.trimIndent()
}
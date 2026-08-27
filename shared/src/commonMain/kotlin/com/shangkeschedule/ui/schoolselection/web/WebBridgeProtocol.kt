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

    // 旧版兼容接口
    window.AndroidBridgePromise = shangkeBridgePromise;
    window.AndroidBridge = shangkeBridge;
})();
""".trimIndent()
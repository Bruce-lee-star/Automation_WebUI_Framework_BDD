package com.hsbc.cmb.hk.dbb.automation.framework.web.monitoring;

import com.hsbc.cmb.hk.dbb.automation.framework.web.utils.LoggingConfigUtil;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Real API Monitor - 实时监控API响应
 * 功能：
 * 1. 实时监控API请求和响应
 * 2. 记录API调用历史（包括真实的响应状态码、响应时间等）
 * 3. 实时验证API响应是否符合预期（状态码、响应时间、响应内容等）
 * 4. 支持按URL、方法等条件过滤API调用记录
 * 5. 不修改API请求和响应，只进行监控
 *
 * 使用方式（推荐使用Builder模式）：
 *
 * 【推荐】Builder模式 - 简单验证（仅状态码）：
 *   RealApiMonitor.with(context)
 *       .monitorApi(".*auth/login.*", 200)
 *       .monitorApi(".*api/users.*", 200)
 *       .build();
 *
 * 【高级】Builder模式 - 多维度验证：
 *   RealApiMonitor.with(context)
 *       .expectApi(ApiExpectation.forUrl(".*auth/login.*")
 *           .statusCode(200)
 *           .responseTimeLessThan(1000)
 *           .responseBodyContains("success"))
 *       .expectApi(ApiExpectation.forUrl(".*api/users.*")
 *           .statusCode(200)
 *           .responseTimeLessThan(500))
 *       .build();
 *
 * 【简化】单API监控验证（仅状态码）：
 *   monitorAndVerify(context, ".*auth/login.*", 200);
 *
 * 【高级】单API多维度验证：
 *   monitorWithExpectation(context, ApiExpectation.forUrl(".*auth/login.*")
 *       .statusCode(200)
 *       .responseTimeLessThan(1000)
 *       .responseBodyContains("token"));
 *
 * 【灵活】只监控不验证：
 *   startMonitoring(context, ".*api/.*");
 */
public class RealApiMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RealApiMonitor.class);

    // 存储所有API调用记录
    private static final List<ApiCallRecord> apiCallHistory = new CopyOnWriteArrayList<>();

    // 存储已注册的监听器（针对BrowserContext）
    private static final Map<BrowserContext, Set<ResponseListener>> contextListeners = new HashMap<>();

    // 存储API期望（URL模式 -> API期望对象）
    private static final Map<String, ApiExpectation> apiExpectations = new HashMap<>();

    // 是否启用实时验证
    private static volatile boolean realTimeValidationEnabled = false;

    // ==================== 简化API（最常用） ====================

    /**
     * 【推荐】使用Builder模式配置API监控
     *
     * @param context Playwright BrowserContext对象
     * @return ApiMonitorBuilder对象，用于链式调用
     *
     * 示例：
     * RealApiMonitor.with(context)
     *     .monitorApi(".*auth/login.*", 200)
     *     .monitorApi(".*api/users.*", 200)
     *     .build();
     */
    public static ApiMonitorBuilder with(BrowserContext context) {
        return new ApiMonitorBuilder(context);
    }

    /**
     * 【简化】监控单个API并实时验证 - 一行代码搞定！
     * 自动清空历史、启用验证、设置期望、开始监控
     *
     * @param context Playwright BrowserContext对象
     * @param urlPattern URL匹配模式（支持普通URL如 "/api/xxx" 或正则如 ".*api/users.*"）
     * @param expectedStatusCode 期望的状态码（如 200）
     *
     * 示例：
     * monitorAndVerify(context, ".*auth/login.*", 200);
     * monitorAndVerify(context, "/api/users", 200); // 自动转换为正则
     */
    public static void monitorAndVerify(BrowserContext context, String urlPattern, int expectedStatusCode) {
        String pattern = toRegexPattern(urlPattern);
        logger.info("========== Starting API monitoring with real-time verification ==========");
        logger.info("Monitoring API: {} (Expected Status: {})", pattern, expectedStatusCode);
        logger.info("Original URL pattern: '{}' -> Converted to: '{}'", urlPattern, pattern);
        clearHistory();
        clearApiExpectations();
        enableRealTimeValidation();
        expectApiStatus(pattern, expectedStatusCode);
        monitorApi(context, pattern);
    }

    /**
     * 【简化】监控多个API并实时验证 - 批量设置
     *
     * @param context Playwright BrowserContext对象
     * @param expectations API期望映射（URL模式 -> 期望状态码，支持普通URL或正则）
     *
     * 示例：
     * monitorMultiple(context, Map.of(
     *     ".*api/users.*", 200,
     *     ".*api/products.*", 200
     * ));
     * // 或使用普通URL
     * monitorMultiple(context, Map.of(
     *     "/api/users", 200,
     *     "/api/products", 200
     * ));
     */
    public static void monitorMultiple(BrowserContext context, Map<String, Integer> expectations) {
        logger.info("========== Starting multiple APIs monitoring with real-time verification ==========");
        logger.info("Monitoring {} APIs with verification", expectations.size());
        // 转换普通URL为正则表达式
        Map<String, Integer> convertedExpectations = new HashMap<>();
        for (Map.Entry<String, Integer> entry : expectations.entrySet()) {
            String pattern = toRegexPattern(entry.getKey());
            convertedExpectations.put(pattern, entry.getValue());
            logger.info("  - API: {} (Expected Status: {})", pattern, entry.getValue());
        }
        clearHistory();
        clearApiExpectations();
        enableRealTimeValidation();
        expectMultipleApiStatus(convertedExpectations);
        monitorAllApi(context);
    }

    /**
     * 【灵活】只监控API，不自动验证 - 灵活手动验证
     *
     * @param context Playwright BrowserContext对象
     * @param urlPattern URL匹配模式（支持普通URL或正则）
     *
     * 示例：
     * startMonitoring(context, ".*api/.*");
     * // ... 执行操作
     * verifyStatus(".*api/users.*", 200); // 手动验证
     */
    public static void startMonitoring(BrowserContext context, String urlPattern) {
        String pattern = toRegexPattern(urlPattern);
        logger.info("========== Starting API monitoring (without automatic verification) ==========");
        logger.info("Monitoring API: {} (Original: '{}')", pattern, urlPattern);
        clearHistory();
        monitorApi(context, pattern);
    }

    /**
     * 【灵活】监控所有API响应
     *
     * @param context Playwright BrowserContext对象
     *
     * 示例：
     * startMonitoringAll(context);
     * // ... 执行操作
     * printAllCapturedApis(); // 查看所有捕获的API
     */
    public static void startMonitoringAll(BrowserContext context) {
        logger.info("========== Starting full API monitoring (all APIs) ==========");
        clearHistory();
        monitorAllApi(context);
    }

    /**
     * 【高级】监控单个API并进行多维度实时验证
     * 支持验证状态码、响应时间、响应内容等
     *
     * @param context Playwright BrowserContext对象
     * @param expectation API期望对象
     *
     * 示例：
     * monitorWithExpectation(context, ApiExpectation.forUrl(".*auth/login.*")
     *     .statusCode(200)
     *     .responseTimeLessThan(1000)
     *     .responseBodyContains("token"));
     */
    public static void monitorWithExpectation(BrowserContext context, ApiExpectation expectation) {
        logger.info("========== Starting API monitoring with multi-dimension verification ==========");
        logger.info("Monitoring API: {} with expectation: {}", expectation.getUrlPattern(), expectation.getDescription());
        clearHistory();
        clearApiExpectations();
        enableRealTimeValidation();
        RealApiMonitor.apiExpectations.put(expectation.getUrlPattern(), expectation);
        monitorApi(context, expectation.getUrlPattern());
    }

    /**
     * 【高级】监控多个API并进行多维度实时验证
     *
     * @param context Playwright BrowserContext对象
     * @param expectations API期望对象列表
     *
     * 示例：
     * monitorWithExpectations(context, List.of(
     *     ApiExpectation.forUrl(".*auth/login.*").statusCode(200).responseTimeLessThan(1000),
     *     ApiExpectation.forUrl(".*api/users.*").statusCode(200).responseBodyContains("data")
     * ));
     */
    public static void monitorWithExpectations(BrowserContext context, List<ApiExpectation> expectations) {
        logger.info("========== Starting multiple APIs monitoring with multi-dimension verification ==========");
        logger.info("Monitoring {} APIs with verification", expectations.size());
        clearHistory();
        clearApiExpectations();
        enableRealTimeValidation();
        for (ApiExpectation expectation : expectations) {
            logger.info("  - {} : {}", expectation.getUrlPattern(), expectation.getDescription());
            RealApiMonitor.apiExpectations.put(expectation.getUrlPattern(), expectation);
        }
        if (expectations.size() == 1) {
            monitorApi(context, expectations.get(0).getUrlPattern());
        } else {
            monitorAllApi(context);
        }
    }

    /**
     * 将普通URL模式转换为正则表达式
     * 如果URL已经是正则表达式（包含.*、\\d等），则原样返回
     * 否则自动添加.*前缀和后缀进行灵活匹配
     *
     * @param urlPattern URL模式（普通URL或正则表达式）
     * @return 正则表达式模式
     *
     * 示例：
     * - "/api/users" -> ".*api/users.*"
     * - "api/users" -> ".*api/users.*"
     * - ".*api/.*" -> ".*api/.*" (已经是正则，不转换)
     */
    private static String toRegexPattern(String urlPattern) {
        if (urlPattern == null || urlPattern.isEmpty()) {
            return ".*";
        }

        // 检查是否已经是正则表达式（包含常见的正则元字符）
        boolean isRegex = urlPattern.contains(".*") || urlPattern.contains("\\d")
                       || urlPattern.contains("?") || urlPattern.contains("+")
                       || urlPattern.contains("\\w") || urlPattern.contains("\\s");

        if (isRegex) {
            return urlPattern; // 已经是正则表达式，直接返回
        }

        // 如果以 / 开头，去掉开头的 /，然后添加 .* 前后缀
        // 例如：/api/users -> .*api/users.*
        String normalized = urlPattern;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return ".*" + normalized + ".*";
    }
    
    /**
     * API调用记录
     */
    public static class ApiCallRecord {
        private final String requestId;
        private final String url;
        private final String method;
        private final long timestamp;
        private final Map<String, String> requestHeaders;
        private final Object requestBody;
        private final int statusCode;
        private final Map<String, String> responseHeaders;
        private final Object responseBody;
        private final long responseTimeMs;
        
        public ApiCallRecord(String requestId, String url, String method, long timestamp,
                           Map<String, String> requestHeaders, Object requestBody,
                           int statusCode, Map<String, String> responseHeaders,
                           Object responseBody, long responseTimeMs) {
            this.requestId = requestId;
            this.url = url;
            this.method = method;
            this.timestamp = timestamp;
            this.requestHeaders = requestHeaders;
            this.requestBody = requestBody;
            this.statusCode = statusCode;
            this.responseHeaders = responseHeaders;
            this.responseBody = responseBody;
            this.responseTimeMs = responseTimeMs;
        }
        
        public String getRequestId() { return requestId; }
        public String getUrl() { return url; }
        public String getMethod() { return method; }
        public long getTimestamp() { return timestamp; }
        public Map<String, String> getRequestHeaders() { return requestHeaders; }
        public Object getRequestBody() { return requestBody; }
        public int getStatusCode() { return statusCode; }
        public Map<String, String> getResponseHeaders() { return responseHeaders; }
        public Object getResponseBody() { return responseBody; }
        public long getResponseTimeMs() { return responseTimeMs; }
        
        @Override
        public String toString() {
            return String.format("ApiCallRecord{url='%s', method='%s', statusCode=%d, responseTime=%dms}",
                    url, method, statusCode, responseTimeMs);
        }
    }
    
    /**
     * 响应监听器接口
     */
    @FunctionalInterface
    public interface ResponseListener {
        void onResponse(Response response, Request request, long responseTimeMs);
    }
    
    /**
     * 监控特定URL的真实API响应（针对BrowserContext）
     *
     * @param context Playwright BrowserContext对象
     * @param urlPattern URL匹配模式（支持正则表达式）
     */
    public static void monitorApi(BrowserContext context, String urlPattern) {
        monitorApi(context, urlPattern, null);
    }
    
    /**
     * 监控特定URL的真实API响应，并提供自定义监听器（针对BrowserContext）
     * 
     * @param context Playwright BrowserContext对象
     * @param urlPattern URL匹配模式（支持正则表达式）
     * @param listener 响应监听器（可为null）
     */
    public static void monitorApi(BrowserContext context, String urlPattern, ResponseListener listener) {
        Pattern pattern = Pattern.compile(urlPattern);
        logger.info("🎯 Setting up API monitor for pattern: {} on BrowserContext", urlPattern);

        // 用于统计响应数量
        final int[] responseCount = {0};

        // 保存监听器引用（先初始化set）
        Set<ResponseListener> listeners = contextListeners.computeIfAbsent(context, k -> new HashSet<>());

        // 添加响应监听器
        ResponseListener responseListener = (response, request, responseTimeMs) -> {
            responseCount[0]++;
            boolean matches = pattern.matcher(response.url()).matches();
            LoggingConfigUtil.logDebugIfVerbose(logger, "🔍 Checking URL: {} matches pattern: {} = {} (Total responses: {})",
                    response.url(), urlPattern, matches, responseCount[0]);

            if (matches) {
                try {
                    String requestId = UUID.randomUUID().toString();
                    Map<String, String> requestHeaders = new HashMap<>(request.headers());
                    Object requestBody = request.postData();

                    Map<String, String> responseHeaders = new HashMap<>(response.headers());
                    Object responseBody = null;

                    // 尝试获取响应体
                    try {
                        responseBody = response.text();
                    } catch (Exception e) {
                        logger.debug("Failed to get response body for: {}", response.url());
                    }

                    ApiCallRecord record = new ApiCallRecord(
                            requestId, response.url(), request.method(), System.currentTimeMillis(),
                            requestHeaders, requestBody, response.status(), responseHeaders,
                            responseBody, responseTimeMs
                    );

                    apiCallHistory.add(record);
                    logger.info("✅ Recorded API call: {} {} - Status: {}",
                            request.method(), response.url(), response.status());

                    // 实时验证：如果启用了实时验证，立即检查API响应
                    if (realTimeValidationEnabled) {
                        validateRealTimeApi(record);
                    }

                } catch (Exception e) {
                    logger.error("Failed to record API call", e);
                }
            }
        };

        // 添加监听器到set
        listeners.add(responseListener);
        if (listener != null) {
            listeners.add(listener);
        }

        logger.info("📡 Registering onResponse listener on BrowserContext, listeners for this context: {}", listeners.size());

        // 使用局部变量避免闭包问题
        final Set<ResponseListener> currentListeners = listeners;

        context.onResponse(response -> {
            LoggingConfigUtil.logDebugIfVerbose(logger, "📡 onResponse event fired! URL: {}, Status: {}", response.url(), response.status());
            // 使用Playwright API获取真实的响应时间
            long responseTimeMs = 0;
            try {
                responseTimeMs = (long) response.request().timing().responseEnd;
                LoggingConfigUtil.logDebugIfVerbose(logger, "📊 Response timing for {}: {}ms", response.url(), responseTimeMs);
            } catch (Exception e) {
                logger.debug("Failed to get response timing for: {}", response.url());
            }

            // 调用内部监听器
            for (ResponseListener rl : currentListeners) {
                try {
                    rl.onResponse(response, response.request(), responseTimeMs);
                } catch (Exception e) {
                    logger.error("Error executing response listener", e);
                }
            }
        });

        logger.info("✅ API monitoring started successfully for pattern: {} on BrowserContext", urlPattern);
    }
    
    /**
     * 监控所有API响应
     *
     * @param context Playwright BrowserContext对象
     */
    public static void monitorAllApi(BrowserContext context) {
        monitorApi(context, ".*");
    }
    
    /**
     * 获取所有API调用记录
     * 
     * @return API调用历史记录列表
     */
    public static List<ApiCallRecord> getApiHistory() {
        return Collections.unmodifiableList(apiCallHistory);
    }
    
    /**
     * 获取特定URL的API调用记录
     * 
     * @param urlPattern URL匹配模式（支持正则表达式）
     * @return 匹配的API调用记录列表
     */
    public static List<ApiCallRecord> getApiHistoryByUrl(String urlPattern) {
        Pattern pattern = Pattern.compile(urlPattern);
        return apiCallHistory.stream()
                .filter(record -> pattern.matcher(record.getUrl()).matches())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取特定HTTP方法的API调用记录
     * 
     * @param method HTTP方法（GET、POST等）
     * @return 匹配的API调用记录列表
     */
    public static List<ApiCallRecord> getApiHistoryByMethod(String method) {
        return apiCallHistory.stream()
                .filter(record -> record.getMethod().equalsIgnoreCase(method))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取特定状态码的API调用记录
     * 
     * @param statusCode HTTP状态码
     * @return 匹配的API调用记录列表
     */
    public static List<ApiCallRecord> getApiHistoryByStatusCode(int statusCode) {
        return apiCallHistory.stream()
                .filter(record -> record.getStatusCode() == statusCode)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最后一次API调用记录
     * 
     * @return 最后一次API调用记录，如果没有则返回null
     */
    public static ApiCallRecord getLastApiCall() {
        if (apiCallHistory.isEmpty()) {
            return null;
        }
        return apiCallHistory.get(apiCallHistory.size() - 1);
    }
    
    /**
     * 获取特定URL的最后一次API调用记录
     *
     * @param urlPattern URL匹配模式（支持正则表达式）
     * @return 最后一次匹配的API调用记录，如果没有则返回null
     */
    public static ApiCallRecord getLastApiCallByUrl(String urlPattern) {
        List<ApiCallRecord> calls = getApiHistoryByUrl(urlPattern);
        if (calls.isEmpty()) {
            return null;
        }
        return calls.get(calls.size() - 1);
    }


    /**
     * 清除所有API调用记录
     */
    public static void clearHistory() {
        apiCallHistory.clear();
        logger.info("API call history cleared");
    }

    /**
     * 停止监控并清理监听器
     *
     * @param context Playwright BrowserContext对象
     */
    public static void stopMonitoring(BrowserContext context) {
        contextListeners.remove(context);
        logger.info("Stopped monitoring and removed listeners for context");
    }
    
    /**
     * 打印所有捕获到的API（用于调试）
     */
    public static void printAllCapturedApis() {
        logger.info("========== All Captured APIs ==========");
        logger.info("Total APIs captured: {}", apiCallHistory.size());
        
        if (apiCallHistory.isEmpty()) {
            logger.info("No API calls captured.");
            return;
        }
        
        for (int i = 0; i < apiCallHistory.size(); i++) {
            ApiCallRecord record = apiCallHistory.get(i);
            logger.info("#{} [{}] {} - Status: {}", 
                    i + 1, record.getMethod(), record.getUrl(), record.getStatusCode());
        }
        logger.info("========================================");
    }

    /**
     * 打印API调用历史摘要
     */
    public static void printApiHistorySummary() {
        logger.info("=== API Call History Summary ===");
        logger.info("Total API calls: {}", apiCallHistory.size());
        
        // 按URL分组统计
        Map<String, Long> urlCount = apiCallHistory.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getUrl(),
                        Collectors.counting()
                ));
        
        // 按状态码分组统计
        Map<Integer, Long> statusCount = apiCallHistory.stream()
                .collect(Collectors.groupingBy(
                        ApiCallRecord::getStatusCode,
                        Collectors.counting()
                ));
        
        logger.info("Calls by URL:");
        urlCount.forEach((url, count) -> 
                logger.info("  {} - {} calls", url, count));
        
        logger.info("Calls by status code:");
        statusCount.forEach((status, count) ->
                logger.info("  {} - {} calls", status, count));
    }

    // ==================== 实时API验证功能 ====================
    
    /**
     * 启用实时API验证
     * 当API响应时，会立即检查是否符合预期，不符合时立即抛出异常
     */
    public static void enableRealTimeValidation() {
        realTimeValidationEnabled = true;
        logger.info("Real-time API validation enabled");
    }

    /**
     * 设置API期望状态码（简单版本）
     * API响应时会自动验证状态码
     *
     * @param urlPattern URL匹配模式（支持正则表达式）
     * @param expectedStatusCode 期望的状态码
     */
    public static void expectApiStatus(String urlPattern, int expectedStatusCode) {
        apiExpectations.put(urlPattern, ApiExpectation.forUrl(urlPattern).statusCode(expectedStatusCode));
        logger.info("Added API expectation: {} -> {}", urlPattern, expectedStatusCode);
    }

    /**
     * 批量设置API期望状态码（简单版本）
     *
     * @param expectations URL模式 -> 期望状态码的映射
     */
    public static void expectMultipleApiStatus(Map<String, Integer> expectations) {
        for (Map.Entry<String, Integer> entry : expectations.entrySet()) {
            apiExpectations.put(entry.getKey(), ApiExpectation.forUrl(entry.getKey()).statusCode(entry.getValue()));
        }
        logger.info("Added {} API expectations", expectations.size());
    }

    /**
     * 设置API期望（高级版本，支持多维度验证）
     *
     * @param expectation API期望对象
     */
    public static void expectApi(ApiExpectation expectation) {
        apiExpectations.put(expectation.getUrlPattern(), expectation);
        logger.info("Added API expectation: {} -> {}", expectation.getUrlPattern(), expectation.getDescription());
    }

    /**
     * 批量设置API期望（高级版本）
     *
     * @param expectations API期望对象列表
     */
    public static void expectMultipleApi(List<ApiExpectation> expectations) {
        for (ApiExpectation expectation : expectations) {
            apiExpectations.put(expectation.getUrlPattern(), expectation);
        }
        logger.info("Added {} API expectations", expectations.size());
    }

    /**
     * 清除所有API期望
     */
    public static void clearApiExpectations() {
        apiExpectations.clear();
        logger.info("Cleared all API expectations");
    }

    /**
     * 实时验证API响应
     * 当API响应时，检查是否有匹配的期望，如果有则验证
     *
     * @param record API调用记录
     */
    private static void validateRealTimeApi(ApiCallRecord record) {
        if (apiExpectations.isEmpty()) {
            return; // 没有设置期望，跳过验证
        }

        // 检查是否有匹配的期望
        for (Map.Entry<String, ApiExpectation> entry : apiExpectations.entrySet()) {
            String urlPattern = entry.getKey();
            ApiExpectation expectation = entry.getValue();

            // 检查URL是否匹配模式
            try {
                Pattern pattern = Pattern.compile(urlPattern);
                if (pattern.matcher(record.getUrl()).matches()) {
                    // 找到匹配的期望，进行多维度验证
                    expectation.validate(record);
                    // 找到匹配后立即返回
                    return;
                }
            } catch (Exception e) {
                logger.warn("Failed to match URL pattern: {}", urlPattern, e);
            }
        }
    }
    
    /**
     * 获取所有已设置的API期望
     *
     * @return API期望映射
     */
    public static Map<String, ApiExpectation> getApiExpectations() {
        return new HashMap<>(apiExpectations);
    }

    /**
     * 截断字符串到指定长度
     *
     * @param str 原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */

    /**
     * 截断字符串到指定长度
     *
     * @param str 原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private static String truncateString(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "... (truncated)";
    }

    // ==================== API Monitor Builder ====================

    /**
     * API监控构建器 - 使用Builder模式配置API监控
     *
     * 示例用法（简单验证）：
     * RealApiMonitor.with(context)
     *     .monitorApi(".*auth/login.*", 200)
     *     .monitorApi(".*api/users.*", 200)
     *     .build();
     *
     * 示例用法（多维度验证）：
     * RealApiMonitor.with(context)
     *     .expectApi(ApiExpectation.forUrl(".*auth/login.*")
     *         .statusCode(200)
     *         .responseTimeLessThan(1000))
     *     .expectApi(ApiExpectation.forUrl(".*api/users.*")
     *         .statusCode(200)
     *         .responseBodyContains("data"))
     *     .build();
     */
    public static class ApiMonitorBuilder {
        private final BrowserContext context;
        private final Map<String, ApiExpectation> apiExpectations = new HashMap<>();
        private boolean autoClearHistory = true;

        private ApiMonitorBuilder(BrowserContext context) {
            this.context = context;
        }

        /**
         * 添加要监控的API及其期望状态码（简单版本）
         *
         * @param urlPattern URL匹配模式（支持普通URL或正则）
         * @param expectedStatusCode 期望的状态码
         * @return this构建器实例
         */
        public ApiMonitorBuilder monitorApi(String urlPattern, int expectedStatusCode) {
            String pattern = toRegexPattern(urlPattern);
            apiExpectations.put(pattern, ApiExpectation.forUrl(pattern).statusCode(expectedStatusCode));
            return this;
        }

        /**
         * 添加要监控的API及其完整期望（高级版本）
         *
         * @param expectation API期望对象
         * @return this构建器实例
         */
        public ApiMonitorBuilder expectApi(ApiExpectation expectation) {
            apiExpectations.put(expectation.getUrlPattern(), expectation);
            return this;
        }

        /**
         * 批量添加要监控的API（简单版本，仅状态码）
         *
         * @param expectations API期望映射
         * @return this构建器实例
         */
        public ApiMonitorBuilder monitorApis(Map<String, Integer> expectations) {
            for (Map.Entry<String, Integer> entry : expectations.entrySet()) {
                String pattern = toRegexPattern(entry.getKey());
                apiExpectations.put(pattern, ApiExpectation.forUrl(pattern).statusCode(entry.getValue()));
            }
            return this;
        }

        /**
         * 是否自动清空历史记录（默认true）
         *
         * @param autoClear true表示自动清空，false表示不清空
         * @return this构建器实例
         */
        public ApiMonitorBuilder autoClearHistory(boolean autoClear) {
            this.autoClearHistory = autoClear;
            return this;
        }

        /**
         * 构建并启动监控
         */
        public void build() {
            logger.info("========== Building API Monitor ==========");
            logger.info("Total APIs to monitor: {}", apiExpectations.size());
            for (Map.Entry<String, ApiExpectation> entry : apiExpectations.entrySet()) {
                logger.info("  - {} -> {}", entry.getKey(), entry.getValue().getDescription());
            }

            if (autoClearHistory) {
                RealApiMonitor.clearHistory();
            }

            RealApiMonitor.clearApiExpectations();

            // 实时验证总是启用
            RealApiMonitor.enableRealTimeValidation();

            if (!apiExpectations.isEmpty()) {
                // 直接将ApiExpectation对象添加到RealApiMonitor的期望映射中
                for (Map.Entry<String, ApiExpectation> entry : apiExpectations.entrySet()) {
                    RealApiMonitor.apiExpectations.put(entry.getKey(), entry.getValue());
                }
            }

            if (apiExpectations.size() == 1) {
                // 只有一个API，使用特定模式监控
                String pattern = apiExpectations.keySet().iterator().next();
                RealApiMonitor.monitorApi(context, pattern);
            } else {
                // 多个API，监控所有API
                RealApiMonitor.monitorAllApi(context);
            }

            logger.info("✅ API Monitor built successfully!");
        }
    }

    // ==================== API Expectation ====================

    /**
     * API期望类 - 支持多维度验证
     *
     * 示例用法：
     * ApiExpectation.forUrl(".*auth/login.*")
     *     .statusCode(200)
     *     .responseTimeLessThan(1000)
     *     .responseBodyContains("token")
     *     .responseHeaderContains("Content-Type", "application/json");
     */
    public static class ApiExpectation {
        private final String urlPattern;
        private Integer expectedStatusCode;
        private Long maxResponseTime;
        private String expectedResponseBodyContent;
        private String expectedResponseHeaderName;
        private String expectedResponseHeaderValue;

        private ApiExpectation(String urlPattern) {
            this.urlPattern = urlPattern;
        }

        /**
         * 创建API期望对象
         *
         * @param urlPattern URL匹配模式（支持普通URL如 "/api/xxx" 或正则如 ".*api/users.*"）
         *                普通URL会自动转换为正则表达式
         * @return ApiExpectation对象
         */
        public static ApiExpectation forUrl(String urlPattern) {
            // 自动将普通URL转换为正则表达式
            String pattern = RealApiMonitor.toRegexPattern(urlPattern);
            return new ApiExpectation(pattern);
        }

        /**
         * 设置期望的状态码
         *
         * @param statusCode 期望的状态码
         * @return this
         */
        public ApiExpectation statusCode(int statusCode) {
            this.expectedStatusCode = statusCode;
            return this;
        }

        /**
         * 设置期望的最大响应时间
         *
         * @param maxTimeMs 最大响应时间（毫秒）
         * @return this
         */
        public ApiExpectation responseTimeLessThan(long maxTimeMs) {
            this.maxResponseTime = maxTimeMs;
            return this;
        }

        /**
         * 设置期望的响应体包含内容
         *
         * @param content 期望包含的内容
         * @return this
         */
        public ApiExpectation responseBodyContains(String content) {
            this.expectedResponseBodyContent = content;
            return this;
        }

        /**
         * 设置期望的响应头
         *
         * @param headerName 响应头名称
         * @param headerValue 期望的响应头值（支持部分匹配）
         * @return this
         */
        public ApiExpectation responseHeaderContains(String headerName, String headerValue) {
            this.expectedResponseHeaderName = headerName;
            this.expectedResponseHeaderValue = headerValue;
            return this;
        }

        /**
         * 获取URL模式
         */
        public String getUrlPattern() {
            return urlPattern;
        }

        /**
         * 获取期望描述
         */
        public String getDescription() {
            StringBuilder desc = new StringBuilder();
            if (expectedStatusCode != null) {
                desc.append("Status=").append(expectedStatusCode);
            }
            if (maxResponseTime != null) {
                if (desc.length() > 0) desc.append(", ");
                desc.append("Time<").append(maxResponseTime).append("ms");
            }
            if (expectedResponseBodyContent != null) {
                if (desc.length() > 0) desc.append(", ");
                desc.append("Body contains '").append(expectedResponseBodyContent).append("'");
            }
            if (expectedResponseHeaderName != null) {
                if (desc.length() > 0) desc.append(", ");
                desc.append("Header[").append(expectedResponseHeaderName).append("] contains '").append(expectedResponseHeaderValue).append("'");
            }
            return desc.length() > 0 ? desc.toString() : "No validation";
        }

        /**
         * 验证API调用记录
         *
         * @param record API调用记录
         * @throws AssertionError 如果验证失败
         */
        public void validate(ApiCallRecord record) {
            List<String> failures = new ArrayList<>();

            // 验证状态码
            if (expectedStatusCode != null && record.getStatusCode() != expectedStatusCode) {
                failures.add(String.format(
                    "Status Code Mismatch: Expected %d, Actual %d",
                    expectedStatusCode, record.getStatusCode()
                ));
            }

            // 验证响应时间
            if (maxResponseTime != null && record.getResponseTimeMs() > maxResponseTime) {
                failures.add(String.format(
                    "Response Time Exceeded: Expected <%dms, Actual %dms",
                    maxResponseTime, record.getResponseTimeMs()
                ));
            }

            // 验证响应体内容
            if (expectedResponseBodyContent != null) {
                String responseBody = String.valueOf(record.getResponseBody());
                if (responseBody == null || !responseBody.contains(expectedResponseBodyContent)) {
                    failures.add(String.format(
                        "Response Body Does Not Contain: Expected '%s' in response",
                        expectedResponseBodyContent
                    ));
                }
            }

            // 验证响应头
            if (expectedResponseHeaderName != null) {
                String actualHeaderValue = record.getResponseHeaders().get(expectedResponseHeaderName);
                if (actualHeaderValue == null || !actualHeaderValue.contains(expectedResponseHeaderValue)) {
                    failures.add(String.format(
                        "Response Header Mismatch: Expected '%s' to contain '%s', Actual '%s'",
                        expectedResponseHeaderName, expectedResponseHeaderValue, actualHeaderValue
                    ));
                }
            }

            // 如果有失败项，抛出异常
            if (!failures.isEmpty()) {
                String errorMsg = String.format(
                    "Real-time API Validation Failed%n" +
                    "URL: %s%n" +
                    "Method: %s%n" +
                    "%s%n" +
                    "Response Body: %s",
                    record.getUrl(),
                    record.getMethod(),
                    String.join("%n", failures),
                    truncateString(String.valueOf(record.getResponseBody()), 500)
                );
                logger.error(errorMsg);
                throw new AssertionError(errorMsg);
            }

            // 验证通过
            logger.info("✅ API monitoring PASSED! URL: {}, Method: {}, Status: {}, Time: {}ms - ({})",
                    record.getUrl(),
                    record.getMethod(),
                    record.getStatusCode(),
                    record.getResponseTimeMs(),
                    getDescription());
        }
    }
}

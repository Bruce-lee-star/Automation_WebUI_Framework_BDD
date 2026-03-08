package com.hsbc.cmb.hk.dbb.automation.framework.web.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求修改器 - 统一修改 HTTP 请求的 Body、Headers、QueryParams、Method
 *
 * 使用示例：
 * RequestModifier modification = new RequestModifier()
 *     .body("{}")                                  // 完全替换 body
 *     .modifyBodyField("userId", "123")            // 设置/修改单个字段
 *     .modifyBodyField("user.name", "John")        // 设置嵌套字段
 *     .removeBodyField("password")                 // 删除字段
 *     .modifyHeader("x-hsbc-111", "1111111")       // 设置 header
 *     .removeHeader("Cookie")                      // 删除 header
 *     .modifyQueryParam("page", "1")               // 设置参数
 *     .removeQueryParam("debug")                   // 删除参数
 *     .method("GET");
 * RequestModifier.modifyRequest(context, "/user/profile/list", modification);
 */
public class ApiRequestModifier {

    private static final Logger logger = LoggerFactory.getLogger(ApiRequestModifier.class);

    /** 操作类型枚举 */
    private enum Operation { SET, REMOVE }

    /** 字段操作包装类 */
    private static class FieldOp {
        final Operation op;
        final Object value;  // SET 操作时有值，REMOVE 时为 null

        FieldOp(Operation op, Object value) {
            this.op = op;
            this.value = value;
        }

        static FieldOp set(Object value) { return new FieldOp(Operation.SET, value); }
        static FieldOp remove() { return new FieldOp(Operation.REMOVE, null); }
    }

    private String body;
    private Map<String, FieldOp> bodyOps = new LinkedHashMap<>();
    private Map<String, FieldOp> headerOps = new LinkedHashMap<>();
    private Map<String, FieldOp> queryParamOps = new LinkedHashMap<>();
    private String method;

    // ==================== Body 操作 ====================

    /** 完全替换请求体 */
    public ApiRequestModifier body(String body) {
        this.body = body;
        return this;
    }

    /** 修改/设置请求体中的某个字段，支持嵌套路径如 "user.name"、"items[0].id" */
    public ApiRequestModifier modifyBodyField(String path, Object value) {
        bodyOps.put(path, FieldOp.set(value));
        return this;
    }

    /** 批量修改请求体字段 */
    public ApiRequestModifier modifyBodyFields(Map<String, Object> fields) {
        fields.forEach((k, v) -> bodyOps.put(k, FieldOp.set(v)));
        return this;
    }

    /** 删除请求体中的某个字段 */
    public ApiRequestModifier removeBodyField(String path) {
        bodyOps.put(path, FieldOp.remove());
        return this;
    }

    /** 批量删除请求体字段 */
    public ApiRequestModifier removeBodyFields(String... paths) {
        for (String path : paths) {
            bodyOps.put(path, FieldOp.remove());
        }
        return this;
    }

    // ==================== Header 操作 ====================

    /** 添加/修改请求头 */
    public ApiRequestModifier modifyHeader(String name, String value) {
        headerOps.put(name, FieldOp.set(value));
        return this;
    }

    /** 批量添加/修改请求头 */
    public ApiRequestModifier modifyHeaders(Map<String, String> headers) {
        headers.forEach((k, v) -> headerOps.put(k, FieldOp.set(v)));
        return this;
    }

    /** 删除请求头 */
    public ApiRequestModifier removeHeader(String name) {
        headerOps.put(name, FieldOp.remove());
        return this;
    }

    /** 批量删除请求头 */
    public ApiRequestModifier removeHeaders(String... names) {
        for (String name : names) {
            headerOps.put(name, FieldOp.remove());
        }
        return this;
    }

    // ==================== QueryParam 操作 ====================

    /** 添加/修改查询参数 */
    public ApiRequestModifier modifyQueryParam(String name, String value) {
        queryParamOps.put(name, FieldOp.set(value));
        return this;
    }

    /** 批量添加/修改查询参数 */
    public ApiRequestModifier modifyQueryParams(Map<String, String> params) {
        params.forEach((k, v) -> queryParamOps.put(k, FieldOp.set(v)));
        return this;
    }

    /** 删除查询参数 */
    public ApiRequestModifier removeQueryParam(String name) {
        queryParamOps.put(name, FieldOp.remove());
        return this;
    }

    /** 批量删除查询参数 */
    public ApiRequestModifier removeQueryParams(String... names) {
        for (String name : names) {
            queryParamOps.put(name, FieldOp.remove());
        }
        return this;
    }

    // ==================== Method 操作 ====================

    /** 修改 HTTP 方法 */
    public ApiRequestModifier method(String method) {
        this.method = method;
        return this;
    }

    // ==================== 辅助方法 ====================

    boolean hasModifications() {
        return body != null || !bodyOps.isEmpty() || !headerOps.isEmpty()
                || !queryParamOps.isEmpty() || method != null;
    }

    boolean hasBodyModifications() {
        return body != null || !bodyOps.isEmpty();
    }

    boolean hasHeaderModifications() {
        return !headerOps.isEmpty();
    }

    boolean hasQueryParamModifications() {
        return !queryParamOps.isEmpty();
    }

    /** 应用 body 修改到原始请求体 */
    private String applyBodyModifications(String originalBody) {
        // 如果完全替换 body 且没有字段操作，直接返回
        if (body != null && bodyOps.isEmpty()) {
            return body;
        }

        String jsonBody = (body != null) ? body : originalBody;
        if (jsonBody == null || jsonBody.isEmpty()) {
            jsonBody = "{}";
        }

        if (bodyOps.isEmpty()) {
            return jsonBody;
        }

        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonBody, JsonObject.class);

            if (jsonObject == null) {
                jsonObject = new JsonObject();
            }

            for (Map.Entry<String, FieldOp> entry : bodyOps.entrySet()) {
                String path = entry.getKey();
                FieldOp op = entry.getValue();

                if (op.op == Operation.REMOVE) {
                    removeJsonValueByPath(jsonObject, path);
                } else {
                    setJsonValueByPath(jsonObject, path, op.value, gson);
                }
            }

            return gson.toJson(jsonObject);
        } catch (Exception e) {
            logger.warn("Failed to apply body modifications: {}", e.getMessage());
            return jsonBody;
        }
    }

    private void setJsonValueByPath(JsonObject root, String path, Object value, Gson gson) {
        String[] parts = path.split("\\.");
        JsonElement current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            String arrayIndex = extractArrayIndex(part);

            if (arrayIndex != null) {
                String arrayName = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(arrayIndex);

                if (current.isJsonObject() && current.getAsJsonObject().has(arrayName)) {
                    JsonElement arrayElem = current.getAsJsonObject().get(arrayName);
                    if (arrayElem.isJsonArray() && arrayElem.getAsJsonArray().size() > index) {
                        current = arrayElem.getAsJsonArray().get(index);
                    }
                }
            } else {
                if (current.isJsonObject()) {
                    JsonObject currentObj = current.getAsJsonObject();
                    if (!currentObj.has(part)) {
                        currentObj.add(part, new JsonObject());
                    }
                    current = currentObj.get(part);
                }
            }
        }

        String lastPart = parts[parts.length - 1];
        String arrayIndex = extractArrayIndex(lastPart);

        if (current.isJsonObject()) {
            String fieldName = (arrayIndex != null) ? lastPart.substring(0, lastPart.indexOf('[')) : lastPart;
            JsonElement jsonValue = convertToJsonElement(value, gson);

            if (arrayIndex != null) {
                int index = Integer.parseInt(arrayIndex);
                JsonObject currentObj = current.getAsJsonObject();
                if (!currentObj.has(fieldName)) {
                    currentObj.add(fieldName, new JsonArray());
                }
                JsonArray array = currentObj.get(fieldName).getAsJsonArray();
                while (array.size() <= index) {
                    array.add(new JsonObject());
                }
                array.set(index, jsonValue);
            } else {
                current.getAsJsonObject().add(fieldName, jsonValue);
            }
        }
    }

    private void removeJsonValueByPath(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonElement current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (current.isJsonObject() && current.getAsJsonObject().has(part)) {
                current = current.getAsJsonObject().get(part);
            } else {
                return;
            }
        }

        String lastPart = parts[parts.length - 1];
        if (current.isJsonObject()) {
            current.getAsJsonObject().remove(lastPart);
        }
    }

    private String extractArrayIndex(String part) {
        int start = part.indexOf('[');
        int end = part.indexOf(']');
        if (start != -1 && end != -1 && end > start + 1) {
            return part.substring(start + 1, end);
        }
        return null;
    }

    private JsonElement convertToJsonElement(Object value, Gson gson) {
        if (value == null) {
            return JsonNull.INSTANCE;
        } else if (value instanceof String) {
            return new JsonPrimitive((String) value);
        } else if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        } else if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        } else if (value instanceof Character) {
            return new JsonPrimitive((Character) value);
        } else {
            return gson.toJsonTree(value);
        }
    }

    // ==================== 静态修改方法 ====================

    /**
     * 统一修改请求 - BrowserContext 版本
     *
     * @param context BrowserContext
     * @param endpoint URL或endpoint
     * @param modification 请求修改配置
     */
    public static void modifyRequest(BrowserContext context, String endpoint, ApiRequestModifier modification) {
        logger.info("========== Unified request modification for: {} ==========", endpoint);
        logger.info("   Body: {}", modification.body);
        logger.info("   BodyOps: {}", modification.bodyOps);
        logger.info("   HeaderOps: {}", modification.headerOps);
        logger.info("   QueryParamOps: {}", modification.queryParamOps);
        logger.info("   Method: {}", modification.method);

        String globPattern = "**" + endpoint + "**";
        logger.info("Registering unified route for endpoint: {} with glob pattern: {}", endpoint, globPattern);

        context.route(globPattern, route -> {
            try {
                Request request = route.request();
                String url = request.url();

                logger.info(">>> ROUTE MATCHED (Unified): {}", url);

                Route.ResumeOptions options = new Route.ResumeOptions();

                // 1. 修改 Body
                if (modification.hasBodyModifications()) {
                    String originalBody = request.postData();
                    String modifiedBody = modification.applyBodyModifications(originalBody);
                    options.setPostData(modifiedBody);
                    logger.info("   -> Original body: {}", originalBody);
                    logger.info("   -> Modified body: {}", modifiedBody);
                }

                // 2. 修改 Headers
                if (modification.hasHeaderModifications()) {
                    Map<String, String> headers = new HashMap<>(request.headers());
                    for (Map.Entry<String, FieldOp> entry : modification.headerOps.entrySet()) {
                        if (entry.getValue().op == Operation.REMOVE) {
                            headers.remove(entry.getKey());
                            logger.info("   -> Removed header: {}", entry.getKey());
                        } else {
                            headers.put(entry.getKey(), (String) entry.getValue().value);
                            logger.info("   -> Set header: {} = {}", entry.getKey(), entry.getValue().value);
                        }
                    }
                    options.setHeaders(headers);
                }

                // 3. 修改 Query Params
                if (modification.hasQueryParamModifications()) {
                    String newUrl = url;
                    for (Map.Entry<String, FieldOp> entry : modification.queryParamOps.entrySet()) {
                        if (entry.getValue().op == Operation.REMOVE) {
                            newUrl = modifyUrlQueryParam(newUrl, entry.getKey(), null);
                            logger.info("   -> Removed query param: {}", entry.getKey());
                        } else {
                            newUrl = modifyUrlQueryParam(newUrl, entry.getKey(), (String) entry.getValue().value);
                            logger.info("   -> Set query param: {} = {}", entry.getKey(), entry.getValue().value);
                        }
                    }
                    options.setUrl(newUrl);
                }

                // 4. 修改 Method
                if (modification.method != null) {
                    options.setMethod(modification.method);
                    logger.info("   -> Setting method: {}", modification.method);
                }

                logger.info(">>> Request modified successfully!");
                route.resume(options);
            } catch (Exception e) {
                logger.error("Error in unified request modification handler", e);
                route.resume();
            }
        });

        logger.info(" Unified request modifier configured successfully!");
    }

    /**
     * 统一修改请求 - Page 版本
     *
     * @param page Page
     * @param endpoint URL或endpoint
     * @param modification 请求修改配置
     */
    public static void modifyRequest(Page page, String endpoint, ApiRequestModifier modification) {
        logger.info("========== Unified request modification for: {} ==========", endpoint);
        logger.info("   Body: {}", modification.body);
        logger.info("   BodyOps: {}", modification.bodyOps);
        logger.info("   HeaderOps: {}", modification.headerOps);
        logger.info("   QueryParamOps: {}", modification.queryParamOps);
        logger.info("   Method: {}", modification.method);

        String globPattern = "**" + endpoint + "**";
        logger.info("Registering unified route for endpoint: {} with glob pattern: {}", endpoint, globPattern);

        page.route(globPattern, route -> {
            try {
                Request request = route.request();
                String url = request.url();

                logger.info(">>> ROUTE MATCHED (Unified Page): {}", url);

                Route.ResumeOptions options = new Route.ResumeOptions();

                // 1. 修改 Body
                if (modification.hasBodyModifications()) {
                    String originalBody = request.postData();
                    String modifiedBody = modification.applyBodyModifications(originalBody);
                    options.setPostData(modifiedBody);
                    logger.info("   -> Original body: {}", originalBody);
                    logger.info("   -> Modified body: {}", modifiedBody);
                }

                // 2. 修改 Headers
                if (modification.hasHeaderModifications()) {
                    Map<String, String> headers = new HashMap<>(request.headers());
                    for (Map.Entry<String, FieldOp> entry : modification.headerOps.entrySet()) {
                        if (entry.getValue().op == Operation.REMOVE) {
                            headers.remove(entry.getKey());
                            logger.info("   -> Removed header: {}", entry.getKey());
                        } else {
                            headers.put(entry.getKey(), (String) entry.getValue().value);
                            logger.info("   -> Set header: {} = {}", entry.getKey(), entry.getValue().value);
                        }
                    }
                    options.setHeaders(headers);
                }

                // 3. 修改 Query Params
                if (modification.hasQueryParamModifications()) {
                    String newUrl = url;
                    for (Map.Entry<String, FieldOp> entry : modification.queryParamOps.entrySet()) {
                        if (entry.getValue().op == Operation.REMOVE) {
                            newUrl = modifyUrlQueryParam(newUrl, entry.getKey(), null);
                            logger.info("   -> Removed query param: {}", entry.getKey());
                        } else {
                            newUrl = modifyUrlQueryParam(newUrl, entry.getKey(), (String) entry.getValue().value);
                            logger.info("   -> Set query param: {} = {}", entry.getKey(), entry.getValue().value);
                        }
                    }
                    options.setUrl(newUrl);
                }

                // 4. 修改 Method
                if (modification.method != null) {
                    options.setMethod(modification.method);
                    logger.info("   -> Setting method: {}", modification.method);
                }

                logger.info(">>> Request modified successfully!");
                route.resume(options);
            } catch (Exception e) {
                logger.error("Error in unified request modification handler", e);
                route.resume();
            }
        });

        logger.info(" Unified request modifier configured successfully!");
    }

    /**
     * 修改 URL 查询参数
     * @param url 原始 URL
     * @param paramName 参数名
     * @param paramValue 参数值（如果为 null，则删除该参数）
     * @return 修改后的 URL
     */
    private static String modifyUrlQueryParam(String url, String paramName, String paramValue) {
        if (url == null) {
            return url;
        }

        try {
            java.net.URI uri = java.net.URI.create(url);

            // 解析查询参数
            String query = uri.getQuery();
            Map<String, String> params = new java.util.LinkedHashMap<>();

            if (query != null && !query.isEmpty()) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    }
                }
            }

            // 修改或删除参数
            if (paramValue == null) {
                params.remove(paramName);
            } else {
                params.put(paramName, paramValue);
            }

            // 重建查询字符串
            StringBuilder newQuery = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (newQuery.length() > 0) {
                    newQuery.append("&");
                }
                newQuery.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"));
                newQuery.append("=");
                newQuery.append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
            }

            // 构建新 URL
            java.net.URI newUri = new java.net.URI(
                uri.getScheme(),
                uri.getAuthority(),
                uri.getPath(),
                newQuery.length() > 0 ? newQuery.toString() : null,
                uri.getFragment()
            );

            return newUri.toString();
        } catch (Exception e) {
            logger.warn("Failed to modify URL query param: {} = {}, error: {}", paramName, paramValue, e.getMessage());
            // 降级处理：简单追加
            String separator = url.contains("?") ? "&" : "?";
            if (paramValue == null) {
                return url; // 无法删除，返回原 URL
            }
            return url + separator + paramName + "=" + paramValue;
        }
    }
}

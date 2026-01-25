package com.hsbc.cmb.dbb.hk.automation.utils.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 文件读取工具
 * 用于从文件系统读取 Mock 数据等 JSON 文件
 * 支持缓存机制，提高性能
 */
public class JsonFileReader {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonFileReader.class);
    
    // 文件内容缓存
    private static final Map<String, String> fileCache = new HashMap<>();
    
    // 是否启用缓存
    private static boolean cacheEnabled = true;
    
    /**
     * 从文件读取 JSON 内容
     * 
     * @param filePath 文件路径（支持相对路径和绝对路径）
     * @return JSON 字符串内容
     */
    public static String readJsonFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.error("File path is null or empty");
            return null;
        }
        
        // 检查缓存
        if (cacheEnabled && fileCache.containsKey(filePath)) {
            logger.debug("Reading from cache: {}", filePath);
            return fileCache.get(filePath);
        }
        
        try {
            Path path = resolvePath(filePath);
            
            // 检查文件是否存在
            if (!Files.exists(path)) {
                logger.error("File not found: {}", path.toAbsolutePath());
                return null;
            }
            
            // 检查是否为文件
            if (!Files.isRegularFile(path)) {
                logger.error("Path is not a regular file: {}", path.toAbsolutePath());
                return null;
            }
            
            // 读取文件内容
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            
            // 验证是否为空
            if (content.trim().isEmpty()) {
                logger.warn("File is empty: {}", filePath);
                return null;
            }
            
            // 验证 JSON 格式（简单检查）
            if (!isValidJsonFormat(content)) {
                logger.warn("File content may not be valid JSON: {}", filePath);
            }
            
            // 存入缓存
            if (cacheEnabled) {
                fileCache.put(filePath, content);
                logger.debug("Cached file content: {}", filePath);
            }
            
            logger.info("Successfully read JSON file: {} ({} bytes)", filePath, content.length());
            return content;
            
        } catch (IOException e) {
            logger.error("Failed to read JSON file: {}", filePath, e);
            return null;
        }
    }
    
    /**
     * 解析文件路径
     * 支持相对路径和绝对路径
     */
    private static Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        
        // 如果是绝对路径，直接返回
        if (path.isAbsolute()) {
            return path;
        }
        
        // 相对路径：尝试多个基础路径
        Path[] basePaths = {
            Paths.get(""),                                    // 项目根目录
            Paths.get("src/test/resources"),                  // 测试资源目录
            Paths.get("src/test/resources/mocks"),            // Mock 数据目录
            Paths.get(System.getProperty("user.dir"))         // 当前工作目录
        };
        
        for (Path basePath : basePaths) {
            Path resolved = basePath.resolve(filePath);
            if (Files.exists(resolved)) {
                logger.debug("Resolved path: {} -> {}", filePath, resolved.toAbsolutePath());
                return resolved;
            }
        }
        
        // 如果都找不到，返回原路径
        logger.debug("Using original path: {}", filePath);
        return path;
    }
    
    /**
     * 简单的 JSON 格式验证
     */
    private static boolean isValidJsonFormat(String content) {
        String trimmed = content.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    /**
     * 启用缓存
     */
    public static void enableCache() {
        cacheEnabled = true;
        logger.info("File cache enabled");
    }
    
    /**
     * 禁用缓存
     */
    public static void disableCache() {
        cacheEnabled = false;
        logger.info("File cache disabled");
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        fileCache.clear();
        logger.info("File cache cleared");
    }
    
    /**
     * 清除指定文件的缓存
     */
    public static void clearCache(String filePath) {
        fileCache.remove(filePath);
        logger.debug("Cleared cache for: {}", filePath);
    }
    
    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return fileCache.size();
    }
    
    /**
     * 检查文件是否存在
     */
    public static boolean fileExists(String filePath) {
        try {
            Path path = resolvePath(filePath);
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            logger.debug("Error checking file existence: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 读取 JSON 文件并验证内容
     * 
     * @param filePath 文件路径
     * @return JSON 内容，如果文件不存在或格式无效则返回 null
     */
    public static String readAndValidateJsonFile(String filePath) {
        if (!fileExists(filePath)) {
            logger.error("JSON file does not exist: {}", filePath);
            return null;
        }
        
        String content = readJsonFile(filePath);
        
        if (content == null) {
            logger.error("Failed to read JSON file or file is empty: {}", filePath);
            return null;
        }
        
        if (!isValidJsonFormat(content)) {
            logger.error("Invalid JSON format in file: {}", filePath);
            return null;
        }
        
        return content;
    }
    
    /**
     * 读取 Mock 数据文件（专用方法）
     * 自动从 mocks 目录读取
     */
    public static String readMockData(String fileName) {
        // 如果已经包含路径，直接读取
        if (fileName.contains("/") || fileName.contains("\\")) {
            return readAndValidateJsonFile(fileName);
        }
        
        // 自动添加 mocks 目录前缀和 .json 后缀
        String[] possiblePaths = {
            "src/test/resources/mocks/" + fileName,
            "src/test/resources/mocks/" + fileName + ".json",
            "mocks/" + fileName,
            "mocks/" + fileName + ".json"
        };
        
        for (String path : possiblePaths) {
            if (fileExists(path)) {
                logger.debug("Found mock data file: {}", path);
                return readAndValidateJsonFile(path);
            }
        }
        
        logger.error("Mock data file not found: {}. Searched in: {}", fileName, String.join(", ", possiblePaths));
        return null;
    }
}

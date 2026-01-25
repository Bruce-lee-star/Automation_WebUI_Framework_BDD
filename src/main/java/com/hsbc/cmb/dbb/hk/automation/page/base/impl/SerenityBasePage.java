package com.hsbc.cmb.dbb.hk.automation.page.base.impl;

import com.microsoft.playwright.Page;
import com.hsbc.cmb.dbb.hk.automation.framework.util.LoggingConfigUtil;

import com.hsbc.cmb.dbb.hk.automation.page.base.BasePage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Serenity 基础页面类
 * 继承自BasePage，添加了Serenity BDD集成功能
 */
public abstract class SerenityBasePage extends BasePage {
    
    private static final Logger logger = LoggerFactory.getLogger(SerenityBasePage.class);
    
    // 存储Serenity测试数据
    private final Map<String, Object> serenityTestData = new HashMap<>();
    
    /**
     * 构造函数
     */
    public SerenityBasePage() {
        super();
        
        LoggingConfigUtil.logInfoIfVerbose(
            logger, "🚀 Initializing Serenity Base Page");
        
        // 记录页面初始化到Serenity报告
        addSerenityTestData("pageInitialized", true);
        addSerenityTestData("pageClass", this.getClass().getSimpleName());
    }
    
    /**
     * 获取当前页面的Page对象
     * 覆盖父类方法，添加Serenity集成
     */
    @Override
    public Page getPage() {
        Page page = super.getPage();
        if (page != null) {
            addSerenityTestData("currentUrl", page.url());
            addSerenityTestData("pageTitle", page.title());
        }
        return page;
    }
    
    /**
     * 添加测试数据到本地存储
     */
    protected void addSerenityTestData(String key, Object value) {
        serenityTestData.put(key, value);
        
        LoggingConfigUtil.logDebugIfVerbose(
            logger, "📝 Added Serenity test data: {} = {}", key, value);
    }
    
    /**
     * 获取Serenity测试数据
     */
    protected Object getSerenityTestData(String key) {
        return serenityTestData.get(key);
    }
    
    /**
     * 验证页面标题是否包含指定文本
     */
    public boolean verifyPageTitleContains(String expectedText) {
        String actualTitle = getTitle();
        boolean contains = actualTitle.contains(expectedText);
        
        if (contains) {
            addSerenityTestData("titleVerification", "PASS");
            addSerenityTestData("expectedTitle", expectedText);
            addSerenityTestData("actualTitle", actualTitle);
        } else {
            addSerenityTestData("titleVerification", "FAIL");
            addSerenityTestData("expectedTitle", expectedText);
            addSerenityTestData("actualTitle", actualTitle);
        }
        
        return contains;
    }
    
    /**
     * 验证页面标题是否等于指定文本
     */
    public boolean verifyPageTitleEquals(String expectedText) {
        String actualTitle = getTitle();
        boolean equals = actualTitle.equals(expectedText);
        
        if (equals) {
            addSerenityTestData("titleVerification", "PASS");
            addSerenityTestData("expectedTitle", expectedText);
            addSerenityTestData("actualTitle", actualTitle);
        } else {
            addSerenityTestData("titleVerification", "FAIL");
            addSerenityTestData("expectedTitle", expectedText);
            addSerenityTestData("actualTitle", actualTitle);
        }
        
        return equals;
    }
    
    /**
     * 验证当前URL是否包含指定文本
     */
    public boolean verifyUrlContains(String expectedText) {
        String actualUrl = getCurrentUrl();
        boolean contains = actualUrl.contains(expectedText);
        
        if (contains) {
            addSerenityTestData("urlVerification", "PASS");
            addSerenityTestData("expectedUrlFragment", expectedText);
            addSerenityTestData("actualUrl", actualUrl);
        } else {
            addSerenityTestData("urlVerification", "FAIL");
            addSerenityTestData("expectedUrlFragment", expectedText);
            addSerenityTestData("actualUrl", actualUrl);
        }
        
        return contains;
    }
    
    /**
     * 点击元素 - 覆盖父类方法，添加Serenity集成
     */
    @Override
    public void click(String selector) {
        logger.info("[Serenity] Clicking element: {}", selector);
        addSerenityTestData("lastAction", "click");
        addSerenityTestData("lastActionElement", selector);
        super.click(selector);
    }
    
    /**
     * 输入文本 - 覆盖父类方法，添加Serenity集成
     */
    @Override
    public void type(String selector, String text) {
        logger.info("[Serenity] Typing text '{}' into element: {}", text, selector);
        addSerenityTestData("lastAction", "type");
        addSerenityTestData("lastActionElement", selector);
        addSerenityTestData("lastActionValue", text);
        super.type(selector, text);
    }
    
    /**
     * 导航到指定URL - 覆盖父类方法，添加Serenity集成
     */
    @Override
    public void navigateTo(String url) {
        logger.info("[Serenity] Navigating to URL: {}", url);
        addSerenityTestData("lastAction", "navigate");
        addSerenityTestData("navigateUrl", url);
        super.navigateTo(url);
    }
    
    /**
     * 获取Serenity测试数据映射
     */
    public Map<String, Object> getSerenityTestDataMap() {
        return new HashMap<>(serenityTestData);
    }
    
    /**
     * 清除Serenity测试数据
     */
    public void clearSerenityTestData() {
        serenityTestData.clear();
        logger.debug("🧹 Cleared all Serenity test data");
    }
    
    /**
     * 记录页面验证信息
     */
    protected void recordPageVerification(String verificationName, boolean passed) {
        String status = passed ? "PASS" : "FAIL";
        addSerenityTestData("verification_" + verificationName, status);
        logger.debug("✅ Verification '{}': {}", verificationName, status);
    }
}

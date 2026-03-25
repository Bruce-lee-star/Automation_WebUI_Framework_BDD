package com.hsbc.cmb.hk.dbb.automation.tests.glue;

import com.hsbc.cmb.hk.dbb.automation.framework.web.annotations.AutoBrowser;
import com.hsbc.cmb.hk.dbb.automation.framework.web.config.BrowserOverrideManager;
import com.hsbc.cmb.hk.dbb.automation.framework.web.lifecycle.PlaywrightManager;
import com.hsbc.cmb.hk.dbb.automation.framework.web.session.SessionManager;
import com.hsbc.cmb.hk.dbb.automation.tests.steps.LoginSteps;
import com.microsoft.playwright.options.ColorScheme;
import io.cucumber.java.en.Given;
import net.serenitybdd.annotations.Steps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logon Glue Code - 登录相关的步骤定义
 *
 * 企业级解决方案：添加@AutoBrowser注解，浏览器自动管理
 *
 * 使用方式：
 * 1. 在Glue类上添加@AutoBrowser注解
 * 2. 完成！AutoBrowserManager会自动管理浏览器
 *
 * 优势：
 * -  零配置：只需一个注解
 * -  零侵入：步骤代码完全不需要修改
 * -  自动化：框架自动处理浏览器切换
 * -  可维护：集中管理，易于扩展
 *
 * 自定义配置机制：
 * - 业务层直接调用 PlaywrightManager 的自定义配置方法
 * - 不需要使用 @Before hooks
 * - 框架在 Context 创建时自动应用自定义配置
 *
 * @author Automation Framework
 * @version 3.0
 */
@AutoBrowser(verbose = true)  // 启用详细日志
public class LogonGlue {

    private static final Logger logger = LoggerFactory.getLogger(LogonGlue.class);

    @Steps
    private LoginSteps loginSteps;

    /**
     * 登录步骤
     *
     * 框架自动处理机制：
     * 1. 调用 SessionManager.prepareSession() 准备 session
     * 2. SessionManager.prepareSession() 内部会调用 PlaywrightManager.setStorageStatePath()
     * 3. 业务层可以在Glue方法中设置自定义配置
     * 4. Context创建时会应用所有自定义配置
     *
     * 注意：框架不会立即重置customContextOptionsFlag,
     *       允许在Glue方法中设置多个自定义配置选项
     *
     * @param env 环境标识
     * @param username 用户名
     */
    @Given("logon DBB {string} environment as user {string}")
    public void logonDBBEnvironmentAsUserGlue(String env, String username) {
        // 打印当前浏览器类型用于调试
        String effectiveBrowser = BrowserOverrideManager.getEffectiveBrowserType();
        String defaultBrowser = BrowserOverrideManager.getDefaultBrowserType();
        logger.info("========================================");
        logger.info("LogonGlue - Scenario: {} as {}", env, username);
        logger.info("  Effective browser: {}", effectiveBrowser);
        logger.info("  Default browser: {}", defaultBrowser);
        logger.info("========================================");

        // 【在Glue方法中设置自定义配置】
        // 框架不会立即重置customContextOptionsFlag,允许多次设置
        PlaywrightManager.setCustomColorScheme(ColorScheme.DARK);
        PlaywrightManager.setCustomUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.0.0 Safari/537.36");
        PlaywrightManager.setCustomViewportSize(600, 840);

        // 【准备session】在业务层直接调用
        String sessionKey = env + "_" + username;
        SessionManager.prepareSession(sessionKey);

        // 执行登录
        loginSteps.logonDBBEnvironmentAsUser(env, username);
    }
}

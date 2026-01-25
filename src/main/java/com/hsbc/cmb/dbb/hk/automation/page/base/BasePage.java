package com.hsbc.cmb.dbb.hk.automation.page.base;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.*;
import com.hsbc.cmb.dbb.hk.automation.framework.core.FrameworkCore;
import com.hsbc.cmb.dbb.hk.automation.framework.lifecycle.PlaywrightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基础页面类 - 所有PageObject的父类
 * 提供通用的页面操作方法和元素定位方法
 * 
 * 使用示例：
 * <pre>
 * public class LoginPage extends BasePage {
 *     private static final String USERNAME_INPUT = "#username";
 *     private static final String PASSWORD_INPUT = "#password";
 *     private static final String LOGIN_BUTTON = "#login-btn";
 *     
 *     public void login(String username, String password) {
 *         type(USERNAME_INPUT, username);
 *         type(PASSWORD_INPUT, password);
 *         click(LOGIN_BUTTON);
 *     }
 * }
 * </pre>
 */
public abstract class BasePage {
    protected static final Logger logger = LoggerFactory.getLogger(BasePage.class);
    
    protected Page page;
    protected BrowserContext context;
    
    /**
     * 构造函数 - 初始化页面对象
     */
    public BasePage() {
        // 确保框架已初始化
        if (!FrameworkCore.getInstance().isInitialized()) {
            FrameworkCore.getInstance().initialize();
        }
        // 不在构造函数中初始化，延迟到第一次使用时
    }

    /**
     * 确保page有效（如果已关闭则重新获取）
     */
    private void ensurePageValid() {
        if (page == null || page.isClosed()) {
            page = PlaywrightManager.getPage();
        }
    }

    /**
     * 确保context有效（如果已关闭则重新获取）
     */
    private void ensureContextValid() {
        if (context == null || context.pages().isEmpty()) {
            context = PlaywrightManager.getContext();
        }
    }

    /**
     * 获取当前页面的 Page 对象
     */
    public Page getPage() {
        ensurePageValid();
        return this.page;
    }

    /**
     * 获取BrowserContext对象
     */
    protected BrowserContext getContext() {
        ensureContextValid();
        return this.context;
    }

    // ==================== 元素定位方法 ====================

    /**
     * 通过选择器查找单个元素
     */
    protected Locator locator(String selector) {
        ensurePageValid();
        return page.locator(selector);
    }

    /**
     * 通过选择器查找多个元素
     */
    protected List<Locator> locators(String selector) {
        ensurePageValid();
        return page.locator(selector).all();
    }

    /**
     * 通过文本查找元素（包含指定文本）
     */
    protected Locator byText(String text) {
        ensurePageValid();
        return page.getByText(text);
    }

    /**
     * 通过文本查找元素（精确匹配）
     */
    protected Locator byExactText(String text) {
        ensurePageValid();
        return page.getByText(text, new Page.GetByTextOptions().setExact(true));
    }

    /**
     * 通过占位符查找输入框
     */
    protected Locator byPlaceholder(String placeholder) {
        ensurePageValid();
        return page.getByPlaceholder(placeholder);
    }

    /**
     * 通过标签查找元素
     */
    protected Locator byLabel(String label) {
        ensurePageValid();
        return page.getByLabel(label);
    }

    /**
     * 通过Alt文本查找图片
     */
    protected Locator byAltText(String altText) {
        ensurePageValid();
        return page.getByAltText(altText);
    }

    /**
     * 通过标题查找元素
     */
    protected Locator byTitle(String title) {
        ensurePageValid();
        return page.getByTitle(title);
    }

    /**
     * 通过Role查找元素
     */
    protected Locator byRole(AriaRole role) {
        ensurePageValid();
        return page.getByRole(role);
    }

    // ==================== 基础操作方法 ====================

    /**
     * 点击元素
     */
    public void click(String selector) {
        logger.info("Clicking element: {}", selector);
        locator(selector).click();
    }

    /**
     * 双击元素
     */
    public void doubleClick(String selector) {
        logger.info("Double clicking element: {}", selector);
        locator(selector).dblclick();
    }

    /**
     * 右键点击元素
     */
    public void rightClick(String selector) {
        logger.info("Right clicking element: {}", selector);
        locator(selector).click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
    }

    /**
     * 悬停在元素上
     */
    public void hover(String selector) {
        logger.info("Hovering over element: {}", selector);
        locator(selector).hover();
    }

    /**
     * 输入文本
     */
    public void type(String selector, String text) {
        logger.info("Typing text '{}' into element: {}", text, selector);
        locator(selector).fill(text);
    }

    /**
     * 清空输入框
     */
    public void clear(String selector) {
        logger.info("Clearing element: {}", selector);
        locator(selector).clear();
    }

    /**
     * 追加文本
     */
    public void append(String selector, String text) {
        logger.info("Appending text '{}' to element: {}", text, selector);
        locator(selector).fill(text);
    }

    /**
     * 获取元素文本
     */
    public String getText(String selector) {
        String text = locator(selector).innerText();
        logger.info("Getting text from element {}: {}", selector, text);
        return text;
    }

    /**
     * 获取输入框的值
     */
    public String getValue(String selector) {
        String value = locator(selector).inputValue();
        logger.info("Getting value from element {}: {}", selector, value);
        return value;
    }

    /**
     * 获取元素的属性值
     */
    public String getAttribute(String selector, String attributeName) {
        String value = locator(selector).getAttribute(attributeName);
        logger.info("Getting attribute '{}' from element {}: {}", attributeName, selector, value);
        return value;
    }

    /**
     * 选择下拉框选项
     */
    public void selectOption(String selector, String value) {
        logger.info("Selecting option '{}' from element: {}", value, selector);
        locator(selector).selectOption(value);
    }

    /**
     * 选择下拉框选项（通过索引）
     */
    public void selectOption(String selector, int index) {
        logger.info("Selecting option at index {} from element: {}", index, selector);
        locator(selector).selectOption(new SelectOption().setIndex(index));
    }

    /**
     * 勾选复选框
     */
    public void check(String selector) {
        logger.info("Checking element: {}", selector);
        locator(selector).check();
    }

    /**
     * 取消勾选复选框
     */
    public void uncheck(String selector) {
        logger.info("Unchecking element: {}", selector);
        locator(selector).uncheck();
    }

    /**
     * 上传文件
     */
    public void uploadFile(String selector, String filePath) {
        logger.info("Uploading file '{}' to element: {}", filePath, selector);
        locator(selector).setInputFiles(Paths.get(filePath));
    }

    // ==================== 等待方法 ====================

    /**
     * 等待元素可见
     */
    public void waitForVisible(String selector) {
        logger.info("Waiting for element to be visible: {}", selector);
        locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /**
     * 等待元素隐藏
     */
    public void waitForHidden(String selector) {
        logger.info("Waiting for element to be hidden: {}", selector);
        locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    /**
     * 等待元素被附加到DOM
     */
    public void waitForAttached(String selector) {
        logger.info("Waiting for element to be attached: {}", selector);
        locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
    }

    /**
     * 等待元素从DOM中分离
     */
    public void waitForDetached(String selector) {
        logger.info("Waiting for element to be detached: {}", selector);
        locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    /**
     * 等待指定时间（毫秒）
     */
    public void wait(int milliseconds) {
        logger.info("Waiting for {} milliseconds", milliseconds);
        ensurePageValid();
        page.waitForTimeout(milliseconds);
    }

    // ==================== 判断方法 ====================

    /**
     * 检查元素是否可见
     */
    public boolean isVisible(String selector) {
        boolean visible = locator(selector).isVisible();
        logger.info("Element {} is visible: {}", selector, visible);
        return visible;
    }

    /**
     * 检查元素是否存在
     */
    public boolean exists(String selector) {
        boolean exists = locator(selector).count() > 0;
        logger.info("Element {} exists: {}", selector, exists);
        return exists;
    }

    /**
     * 检查元素是否被选中
     */
    public boolean isChecked(String selector) {
        boolean checked = locator(selector).isChecked();
        logger.info("Element {} is checked: {}", selector, checked);
        return checked;
    }

    /**
     * 检查元素是否启用
     */
    public boolean isEnabled(String selector) {
        boolean enabled = locator(selector).isEnabled();
        logger.info("Element {} is enabled: {}", selector, enabled);
        return enabled;
    }

    /**
     * 检查元素是否禁用
     */
    public boolean isDisabled(String selector) {
        boolean disabled = locator(selector).isDisabled();
        logger.info("Element {} is disabled: {}", selector, disabled);
        return disabled;
    }

    // ==================== 页面导航方法 ====================

    /**
     * 导航到指定URL
     */
    public void navigateTo(String url) {
        logger.info("Navigating to URL: {}", url);
        ensurePageValid();
        page.navigate(url);
    }

    /**
     * 刷新页面
     */
    public void refresh() {
        logger.info("Refreshing page");
        ensurePageValid();
        page.reload();
    }

    /**
     * 前进
     */
    public void forward() {
        logger.info("Going forward");
        ensurePageValid();
        page.goForward();
    }

    /**
     * 后退
     */
    public void back() {
        logger.info("Going back");
        ensurePageValid();
        page.goBack();
    }

    /**
     * 获取当前页面URL
     */
    public String getCurrentUrl() {
        ensurePageValid();
        String url = page.url();
        logger.info("Current URL: {}", url);
        return url;
    }

    /**
     * 获取页面标题
     */
    public String getTitle() {
        ensurePageValid();
        String title = page.title();
        logger.info("Page title: {}", title);
        return title;
    }

    // ==================== JavaScript执行方法 ====================

    /**
     * 执行JavaScript代码
     */
    public Object executeJavaScript(String script, Object... args) {
        logger.info("Executing JavaScript: {}", script);
        ensurePageValid();
        return page.evaluate(script, args);
    }

    /**
     * 滚动到页面顶部
     */
    public void scrollToTop() {
        logger.info("Scrolling to top of page");
        ensurePageValid();
        page.evaluate("window.scrollTo(0, 0)");
    }

    /**
     * 滚动到页面底部
     */
    public void scrollToBottom() {
        logger.info("Scrolling to bottom of page");
        ensurePageValid();
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
    }

    /**
     * 滚动到指定元素
     */
    public void scrollToElement(String selector) {
        logger.info("Scrolling to element: {}", selector);
        locator(selector).scrollIntoViewIfNeeded();
    }

    // ==================== 弹窗处理方法 ====================

    /**
     * 接受弹窗
     */
    public void acceptAlert() {
        logger.info("Accepting alert");
        ensurePageValid();
        page.onDialog(dialog -> dialog.accept());
    }

    /**
     * 取消弹窗
     */
    public void dismissAlert() {
        logger.info("Dismissing alert");
        ensurePageValid();
        page.onDialog(dialog -> dialog.dismiss());
    }

    /**
     * 在弹窗中输入文本并接受
     */
    public void acceptPrompt(String text) {
        logger.info("Accepting prompt with text: {}", text);
        ensurePageValid();
        page.onDialog(dialog -> {
            dialog.accept(text);
        });
    }

    // ==================== 截图和记录方法 ====================

    /**
     * 截取全屏截图
     */
    public void takeScreenshot() {
        logger.info("Taking full page screenshot");
        ensurePageValid();
        page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    /**
     * 截取指定元素截图
     */
    public void takeElementScreenshot(String selector) {
        logger.info("Taking screenshot of element: {}", selector);
        locator(selector).screenshot();
    }

    // ==================== 验证方法 ====================

    /**
     * 验证元素文本是否包含指定文本
     */
    public boolean textContains(String selector, String expectedText) {
        String actualText = getText(selector);
        boolean contains = actualText.contains(expectedText);
        logger.info("Text verification - Element '{}' contains '{}': {}", selector, expectedText, contains);
        return contains;
    }

    /**
     * 验证元素文本是否等于指定文本
     */
    public boolean textEquals(String selector, String expectedText) {
        String actualText = getText(selector);
        boolean equals = actualText.equals(expectedText);
        logger.info("Text verification - Element '{}' equals '{}': {}", selector, expectedText, equals);
        return equals;
    }

    /**
     * 验证元素文本是否匹配正则表达式
     */
    public boolean textMatches(String selector, String regex) {
        String actualText = getText(selector);
        boolean matches = Pattern.matches(regex, actualText);
        logger.info("Text verification - Element '{}' matches '{}': {}", selector, regex, matches);
        return matches;
    }

    // ==================== 表单操作方法 ====================

    /**
     * 填写表单
     *
     * @param formData 键值对，键为选择器，值为输入的文本
     */
    public void fillForm(java.util.Map<String, String> formData) {
        logger.info("Filling form with {} fields", formData.size());
        for (java.util.Map.Entry<String, String> entry : formData.entrySet()) {
            type(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 提交表单
     */
    public void submitForm(String formSelector) {
        logger.info("Submitting form: {}", formSelector);
        locator(formSelector).evaluate("form => form.submit()");
    }

    // ==================== Cookie和存储方法 ====================

    /**
     * 添加Cookie
     */
    public void addCookie(String name, String value) {
        logger.info("Adding cookie: {} = {}", name, value);
        getContext().addCookies(Collections.singletonList(new Cookie(name, value)));
    }

    /**
     * 获取所有Cookies
     */
    public List<Cookie> getCookies() {
        return getContext().cookies();
    }

    /**
     * 清除所有Cookies
     */
    public void clearCookies() {
        logger.info("Clearing all cookies");
        getContext().clearCookies();
    }

    // ==================== 调试方法 ====================

    /**
     * 暂停执行（用于调试）
     */
    public void pause() {
        logger.info("Pausing execution for debugging");
        ensurePageValid();
        page.pause();
    }

    /**
     * 打印页面日志
     */
    public void logPageInfo() {
        ensurePageValid();
        logger.info("Page Information:");
        logger.info("URL: {}", page.url());
        logger.info("Title: {}", page.title());
        logger.info("Viewport: {}", page.viewportSize());
    }
}
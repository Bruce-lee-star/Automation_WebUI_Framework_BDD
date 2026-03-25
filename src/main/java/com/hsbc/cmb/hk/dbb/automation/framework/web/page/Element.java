package com.hsbc.cmb.hk.dbb.automation.framework.web.page;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 页面元素注解 - 用于标记Page中的元素字段
 *
 * 使用示例：
 * <pre>
 * public class LoginPage extends SerenityBasePage {
 *     // 单个元素
 *     @Element("#userName")
 *     public PageElement USERNAME_INPUT;
 *
 *     // 元素列表（动态查询所有匹配元素）
 *     @Element("[data-i18n='button_logon']")
 *     public List<PageElement> loginButtons;
 * }
 * </pre>
 *
 * List<PageElement> 使用示例：
 * <pre>
 * // 遍历所有按钮
 * for (PageElement button : loginButtons) {
 *     button.click();
 * }
 *
 * // 获取第一个按钮
 * loginButtons.get(0).click();
 *
 * // 获取元素数量
 * int count = loginButtons.size();
 *
 * // 检查是否存在元素
 * if (!loginButtons.isEmpty()) {
 *     loginButtons.first().click();
 * }
 * </pre>
 *
 * 注意：
 * 1. 选择器字符串可以是任意Playwright支持的选择器（CSS, XPath, Text等）
 * 2. 无需区分选择器类型，Playwright会自动识别
 * 3. 支持单个PageElement和List<PageElement>两种类型
 * 4. List<PageElement>是动态列表,每次访问都会重新查询匹配的元素
 * 5. 字段会自动初始化，无需手动new PageElement()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Element {

    /**
     * 元素选择器
     * 支持所有Playwright选择器类型：CSS, XPath, Text等
     * Playwright会自动识别选择器类型
     */
    String value();

    /**
     * 元素描述（可选）
     * 用于日志和错误信息
     */
    String description() default "";
}

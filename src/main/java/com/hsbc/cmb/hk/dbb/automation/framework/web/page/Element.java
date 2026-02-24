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
 *     @Element("#userName")
 *     public PageElement USERNAME_INPUT;
 *
 *     @Element("[data-i18n='button_next']")
 *     public PageElement NEXT_BUTTON;
 * }
 * </pre>
 *
 * 注意：
 * 1. 选择器字符串可以是任意Playwright支持的选择器（CSS, XPath, Text等）
 * 2. 无需区分选择器类型，Playwright会自动识别
 * 3. 字段会自动初始化，无需手动new PageElement()
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

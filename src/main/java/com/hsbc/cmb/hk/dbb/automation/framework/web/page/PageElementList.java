package com.hsbc.cmb.hk.dbb.automation.framework.web.page;

import com.hsbc.cmb.hk.dbb.automation.framework.web.page.base.BasePage;
import com.microsoft.playwright.Locator;

import java.util.AbstractList;
import java.util.List;

/**
 * PageElement的动态列表
 * 每次访问列表时都会重新查询匹配的元素
 * 
 * 使用示例：
 * <pre>
 * @Element("[data-i18n='button_logon']")
 * public List<PageElement> loginButtons;
 * 
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
 * </pre>
 */
public class PageElementList extends AbstractList<PageElement> {
    private final String selector;
    private final BasePage page;
    
    public PageElementList(String selector, BasePage page) {
        if (selector == null || selector.trim().isEmpty()) {
            throw new IllegalArgumentException("Selector cannot be null or empty");
        }
        this.selector = selector;
        this.page = page;
    }
    
    /**
     * 获取选择器
     */
    public String getSelector() {
        return selector;
    }
    
    /**
     * 获取Page实例
     */
    private BasePage getPage() {
        if (page == null) {
            BasePage currentPage = BasePage.getCurrentPage();
            if (currentPage == null) {
                throw new IllegalStateException("No BasePage instance found. Please ensure page is initialized.");
            }
            return currentPage;
        }
        return page;
    }
    
    /**
     * 获取所有匹配的Locator
     */
    public List<Locator> allLocators() {
        return getPage().locator(selector).all();
    }
    
    @Override
    public PageElement get(int index) {
        List<Locator> locators = allLocators();
        if (index < 0 || index >= locators.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + locators.size());
        }
        return new PageElementWithIndex(selector, page, index);
    }
    
    @Override
    public int size() {
        return allLocators().size();
    }
    
    /**
     * 获取Locator（用于定位所有匹配元素）
     */
    public Locator locator() {
        return getPage().locator(selector);
    }
    
    /**
     * 检查是否存在匹配的元素
     */
    public boolean isEmpty() {
        return size() == 0;
    }
    
    /**
     * 获取第一个元素（如果存在）
     */
    public PageElement first() {
        if (isEmpty()) {
            throw new IllegalStateException("No elements found for selector: " + selector);
        }
        return get(0);
    }
    
    /**
     * 获取最后一个元素（如果存在）
     */
    public PageElement last() {
        if (isEmpty()) {
            throw new IllegalStateException("No elements found for selector: " + selector);
        }
        return get(size() - 1);
    }
    
    /**
     * 检查是否存在至少一个元素
     */
    public boolean hasElements() {
        return !isEmpty();
    }
    
    /**
     * 等待元素出现
     */
    public void waitFor(int timeoutSeconds) {
        getPage().waitForElementExists(selector, timeoutSeconds);
    }
    
    /**
     * 带索引的PageElement
     */
    private static class PageElementWithIndex extends PageElement {
        private final int index;
        
        public PageElementWithIndex(String selector, BasePage page, int index) {
            super(selector, page);
            this.index = index;
        }
        
        @Override
        public Locator locator() {
            return super.locator().nth(index);
        }
        
        /**
         * 获取索引
         */
        public int getIndex() {
            return index;
        }
    }
}

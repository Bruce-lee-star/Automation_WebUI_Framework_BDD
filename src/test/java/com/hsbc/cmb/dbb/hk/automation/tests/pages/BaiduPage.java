package com.hsbc.cmb.dbb.hk.automation.tests.pages;

import com.microsoft.playwright.Locator;
import com.hsbc.cmb.dbb.hk.automation.page.base.impl.SerenityBasePage;

import static org.hamcrest.MatcherAssert.assertThat;


public class BaiduPage extends SerenityBasePage {
    // 百度首页元素
    private static final String SEARCH_INPUT = "#kw";
    private static final String SEARCH_BUTTON = "#su";

    // 搜索结果元素
    private static final String RESULT_CONTENT = "#content_left";
    private static final String RESULT_TITLE = "h3.t a";

    /**
     * 在搜索框输入文本
     */
    public void enterSearchText(String text) {
        Locator searchBox = page.locator(SEARCH_INPUT);
        searchBox.fill(text);
    }

    /**
     * 点击搜索按钮
     */
    public void clickSearchButton() {
        Locator searchBtn = page.locator(SEARCH_BUTTON);
        searchBtn.click();
    }

    /**
     * 验证搜索结果页面加载
     */
    public boolean isSearchResultLoaded() {
        return isVisible(RESULT_CONTENT);
    }

    /**
     * 验证结果中包含指定文本
     */
    public boolean resultContainsText(String text) {
        waitForVisible(RESULT_CONTENT);
        Locator results = locator(RESULT_TITLE);
        int count = results.count();
        boolean found = false;
        for(int i = 0; i < count; i++){
            Locator oneResult = results.nth(i);
            String title = oneResult.innerText();
            if (title.toLowerCase().contains(text.toLowerCase())) {
                found = true;
            }
        }
        assertThat("搜索结果中应该包含: " + text, found, org.hamcrest.Matchers.is(true));
        return found;
    }

    /**
     * 获取当前页面标题
     */
    public String getPageTitle() {
        return super.getTitle();
    }
}

package com.hsbc.cmb.dbb.hk.automation.tests.steps;


import com.hsbc.cmb.dbb.hk.automation.framework.lifecycle.PlaywrightManager;
import com.hsbc.cmb.dbb.hk.automation.page.factory.PageObjectFactory;
import com.hsbc.cmb.dbb.hk.automation.tests.pages.BaiduPage;
import net.serenitybdd.annotations.Step;

public class BaiduSteps {

    // 使用PageObjectFactory自动管理PageObject实例
    // 单例模式，在整个测试运行期间复用同一个实例
    private final BaiduPage baiduPage = PageObjectFactory.getPage(BaiduPage.class);


    @Step
    public void navigateHomePage() {
        baiduPage.navigateTo("https://www.baidu.com");
    }

    @Step
    public void verifyHomePage() {
        baiduPage.getPageTitle();
    }
    @Step
    public void searchKeywords(String keywords) {
        baiduPage.enterSearchText(keywords);
    }

    @Step
    public void clickSearchBtn() {
        baiduPage.clickSearchButton();
    }

    @Step
    public void searchResults(String keywords) {
        baiduPage.isSearchResultLoaded();
        PlaywrightManager.takeScreenshot("搜索结果页面");
        baiduPage.resultContainsText(keywords);
    }
}

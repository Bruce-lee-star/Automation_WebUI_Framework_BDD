package com.hsbc.cmb.dbb.hk.automation.tests.glue;

import com.hsbc.cmb.dbb.hk.automation.tests.steps.BaiduSteps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.annotations.Steps;


/**
 * 百度搜索步骤定义 - 用于Cucumber测试
 */
public class BaiduStepsGlue {
    @Steps
    private BaiduSteps baiduSteps;

    @Given("我打开百度首页")
    public void i_open_baidu_homepage() {
        baiduSteps.navigateHomePage();
    }


    @When("我在搜索框中输入{string}")
    public void i_enter_search_text(String keyword) {
        baiduSteps.searchKeywords(keyword);
    }

    @When("我点击搜索按钮")
    public void i_click_search_button() {
        baiduSteps.clickSearchBtn();
    }

    @Then("百度首页应该正确加载")
    public void baidu_homepage_should_load_correctly() {
        baiduSteps.verifyHomePage();
    }

    @Then("搜索结果页面应该显示{string}")
    public void search_results_should_contain(String expectedText) {
        baiduSteps.searchResults(expectedText);
    }
}

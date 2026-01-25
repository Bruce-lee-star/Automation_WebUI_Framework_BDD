
Feature: 百度搜索功能-2
  作为用户
  我希望能够访问百度首页并进行搜索
  以便找到所需信息

  @test
  Scenario: 访问百度首页
    When 我打开百度首页
    Then 百度首页应该正确加载

  Scenario: 执行百度搜索
    Given 我打开百度首页
    When 我在搜索框中输入"Playwright"
    And 我点击搜索按钮
    Then 搜索结果页面应该显示"Playwright"
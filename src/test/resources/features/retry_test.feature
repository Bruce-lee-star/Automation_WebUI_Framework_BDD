@test
Feature: 重试机制验证测试

  Scenario: 故意失败的测试 - 验证重试机制
    Given 我打开百度首页
    When 我在搜索框中输入"nonexistent_query_12345xyz"
    And 我点击搜索按钮
    Then 搜索结果页面应该显示"这个查询永远不会存在_abc"

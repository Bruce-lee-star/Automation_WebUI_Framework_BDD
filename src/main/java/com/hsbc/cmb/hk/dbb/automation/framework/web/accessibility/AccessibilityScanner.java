package com.hsbc.cmb.hk.dbb.automation.framework.web.accessibility;

import com.hsbc.cmb.hk.dbb.automation.framework.web.lifecycle.PlaywrightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Accessibility Test Collector (Simplified Version)
 * Collects accessibility test results for all pages during test execution
 * Users only need to provide descriptive page names, e.g.: "Login Page", "Home Page", "Order Input Page"
 * 
 * Features:
 * - Individual HTML report for each page
 * - Final aggregated report
 * - Page object automatically retrieved from PlaywrightManager
 * - All reports saved to target/accessibility/ directory
 */
public class AccessibilityScanner {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilityScanner.class);

    // ThreadLocal for thread safety
    private static final ThreadLocal<List<PageResult>> results = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<String>> generatedReports = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<LocalDateTime> testStartTime = ThreadLocal.withInitial(LocalDateTime::now);

    /**
     * Single page accessibility test result
     */
    public static class PageResult {
        private String pageName;
        private String pageUrl;
        private String pageTitle;
        private LocalDateTime testTime;
        private int totalIssues;
        private Map<AccessibilityEngine.IssueSeverity, Integer> issueCounts;
        private List<AccessibilityEngine.AccessibilityIssue> issues;
        private double passRate;
        private boolean passed;

        public PageResult(String pageName, String pageUrl, String pageTitle) {
            this.pageName = pageName;
            this.pageUrl = pageUrl;
            this.pageTitle = pageTitle;
            this.testTime = LocalDateTime.now();
            this.issueCounts = new EnumMap<>(AccessibilityEngine.IssueSeverity.class);
            this.issues = new ArrayList<>();
        }

        // Getters and Setters
        public String getPageName() { return pageName; }
        public String getPageUrl() { return pageUrl; }
        public String getPageTitle() { return pageTitle; }
        public LocalDateTime getTestTime() { return testTime; }
        public int getTotalIssues() { return totalIssues; }
        public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }
        public Map<AccessibilityEngine.IssueSeverity, Integer> getIssueCounts() { return issueCounts; }
        public List<AccessibilityEngine.AccessibilityIssue> getIssues() { return issues; }
        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }

        public void addIssue(AccessibilityEngine.AccessibilityIssue issue) {
            issues.add(issue);
            issueCounts.put(issue.getSeverity(), issueCounts.getOrDefault(issue.getSeverity(), 0) + 1);
            totalIssues = issues.size();
        }
    }

    /**
     * Initialize collector (call before test starts)
     */
    public static void initialize() {
        results.set(new ArrayList<>());
        generatedReports.set(new ArrayList<>());
        testStartTime.set(LocalDateTime.now());
        logger.info("AccessibilityScanner initialized");
    }

    /**
     * Check page and collect results (Simplified API)
     * User only needs to provide descriptive page name, Page object is automatically retrieved from PlaywrightManager
     *
     * @param pageName Page name, e.g.: "Login Page", "Home Page", "Order Input Page"
     * @return Test result
     */
    public static PageResult checkAndCollect(String pageName) {
        try {
            logger.info("Collecting accessibility results for: {}", pageName);

            // Retrieve Page object from PlaywrightManager
            com.microsoft.playwright.Page page = PlaywrightManager.getPage();
            
            if (page == null || page.isClosed()) {
                logger.error("Page is null or closed for accessibility check: {}", pageName);
                throw new IllegalStateException("Page is not available from PlaywrightManager");
            }

            // Execute accessibility check (automatically includes screenshots)
            List<AccessibilityEngine.AccessibilityIssue> issues =
                AccessibilityEngine.checkPageAccessibilityEnhanced(page, true);

            // Create result object
            PageResult result = new PageResult(
                pageName,
                page.url(),
                page.title()
            );

            // Add all issues
            for (AccessibilityEngine.AccessibilityIssue issue : issues) {
                result.addIssue(issue);
            }

            // Calculate statistics
            AccessibilityEngine.TestStatistics stats =
                AccessibilityEngine.calculateStatistics(issues, 1);

            result.setTotalIssues(issues.size());
            result.setPassRate(stats.getPassRate());

            // Determine if passed (no critical and high priority issues)
            long highPriorityCount = issues.stream()
                .filter(i -> i.getSeverity() == AccessibilityEngine.IssueSeverity.CRITICAL ||
                            i.getSeverity() == AccessibilityEngine.IssueSeverity.HIGH)
                .count();

            result.setPassed(highPriorityCount == 0);

            // Add to collector
            results.get().add(result);

            // Generate individual HTML report for each page
            String reportPath = generateIndividualPageReport(result);
            generatedReports.get().add(reportPath);

            logger.info("Accessibility check completed for: {}, issues: {}, passed: {}, report: {}",
                pageName, issues.size(), result.isPassed(), reportPath);

            return result;

        } catch (Exception e) {
            logger.error("Error collecting accessibility results for: {}", pageName, e);
            throw new RuntimeException("Failed to collect accessibility results: " + pageName, e);
        }
    }

    /**
     * Generate individual HTML report for a single page
     *
     * @param result Page test result
     * @return Generated report file path
     */
    private static String generateIndividualPageReport(PageResult result) {
        try {
            // Generate hash value for file name (without accessibility- prefix)
            String hashInput = result.getPageName() + "_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
            String hash = generateHash(hashInput);
            String fileName = String.format("%s.html", hash);
            String reportPath = "target/accessibility/" + fileName;

            // Generate HTML report content
            String htmlReport = generatePageHtmlReport(result);

            // Save report
            saveReport(htmlReport, reportPath);

            logger.info("Individual page report generated: {}", reportPath);
            return reportPath;

        } catch (Exception e) {
            logger.error("Failed to generate individual page report for: {}", result.getPageName(), e);
            throw new RuntimeException("Failed to generate individual page report: " + result.getPageName(), e);
        }
    }

    /**
     * Generate SHA-256 hash value for creating unique file name
     *
     * @param input Input string
     * @return SHA-256 hash value in hexadecimal format
     */
    private static String generateHash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            logger.warn("Failed to generate hash, using fallback method", e);
            return Long.toHexString(System.currentTimeMillis()) +
                    Long.toHexString(System.nanoTime()) +
                    Long.toHexString(Thread.currentThread().getId());
        }
    }

    /**
     * Get all collected results
     */
    public static List<PageResult> getCollectedResults() {
        return results.get();
    }

    /**
     * Generate final aggregated report (call at end of test)
     * Summarizes accessibility test results for all pages and generates a comprehensive report
     */
    public static void generateFinalReport() {
        try {
            List<PageResult> allResults = results.get();
            List<String> individualReports = generatedReports.get();

            if (allResults.isEmpty()) {
                logger.warn("No accessibility results collected");
                return;
            }

            logger.info("Generating final aggregated accessibility report with {} page results", allResults.size());

            // Calculate overall statistics
            int totalIssues = allResults.stream().mapToInt(PageResult::getTotalIssues).sum();
            int passedTests = (int) allResults.stream().filter(PageResult::isPassed).count();
            double overallPassRate = allResults.stream()
                .mapToDouble(PageResult::getPassRate)
                .average()
                .orElse(0.0);

            // Merge all issues
            Map<AccessibilityEngine.IssueSeverity, Integer> overallIssueCounts = new EnumMap<>(AccessibilityEngine.IssueSeverity.class);
            List<AccessibilityEngine.AccessibilityIssue> allIssues = new ArrayList<>();

            for (PageResult result : allResults) {
                allIssues.addAll(result.getIssues());
                for (Map.Entry<AccessibilityEngine.IssueSeverity, Integer> entry : result.getIssueCounts().entrySet()) {
                    overallIssueCounts.put(entry.getKey(),
                        overallIssueCounts.getOrDefault(entry.getKey(), 0) + entry.getValue());
                }
            }

            // Generate aggregated HTML report
            String htmlReport = generateFinalHtmlReport(allResults, totalIssues,
                passedTests, overallPassRate, overallIssueCounts, allIssues, individualReports);

            // Generate file name for aggregated report (using timestamp, not hash)
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String reportPath = "target/accessibility/aggregated-report-" + timestamp + ".html";
            saveReport(htmlReport, reportPath);

            logger.info("Final aggregated accessibility report generated: {}", reportPath);
            logger.info("Summary: {} pages, {} issues, {} passed, pass rate {}%",
                allResults.size(), totalIssues, passedTests, String.format("%.1f", overallPassRate));
            logger.info("Individual reports: {}", String.join(", ", individualReports));

        } catch (Exception e) {
            logger.error("Failed to generate final accessibility report", e);
        }
    }

    /**
     * Generate single page HTML report
     */
    private static String generatePageHtmlReport(PageResult result) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\" />\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        html.append("    <title>").append(result.getPageName()).append(" - Accessibility Test Report</title>\n");
        html.append("    <style>\n");
        html.append(getReportStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"report\">\n");

        // Title
        html.append("        <h1 class=\"title\">").append(result.getPageName()).append(" - Accessibility Test Report</h1>\n");
        html.append("        <p class=\"subtitle\">WCAG 2.2 AA Standard</p>\n");
        html.append("        <p class=\"subtitle\">Page URL: ").append(result.getPageUrl()).append("</p>\n");
        html.append("        <p class=\"subtitle\">Test Time: ")
              .append(result.getTestTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
              .append("</p>\n");

        // Test result
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>📊 Test Result</h2>\n");
        html.append("            <div class=\"result-status\" style=\"background: ")
              .append(result.isPassed() ? "#d4edda" : "#f8d7da")
              .append("; border-left: 4px solid ")
              .append(result.isPassed() ? "#28a745" : "#dc3545")
              .append(";\">\n");
        html.append("                <h3 style=\"color: ")
              .append(result.isPassed() ? "#155724" : "#721c24")
              .append(";\">")
              .append(result.isPassed() ? "✅ Test Passed" : "❌ Test Failed")
              .append("</h3>\n");
        html.append("                <p>Issues Found: <b>").append(result.getTotalIssues()).append("</b></p>\n");
        html.append("                <p>Pass Rate: <b>").append(String.format("%.1f%%", result.getPassRate())).append("</b></p>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");

        // Issue severity statistics
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>⚠️ Issue Severity Statistics</h2>\n");
        html.append("            <div class=\"stats\">\n");

        for (AccessibilityEngine.IssueSeverity severity : new AccessibilityEngine.IssueSeverity[]{
                AccessibilityEngine.IssueSeverity.CRITICAL,
                AccessibilityEngine.IssueSeverity.HIGH,
                AccessibilityEngine.IssueSeverity.MEDIUM,
                AccessibilityEngine.IssueSeverity.LOW}) {
            int count = result.getIssueCounts().getOrDefault(severity, 0);
            html.append("                <div class=\"stat-item\">\n");
            html.append("                    ").append(severity.getDisplayName());
            html.append("                    <div class=\"num\" style=\"color: ")
                  .append(severity.getColor()).append(";\">").append(count).append("</div>\n");
            html.append("                </div>\n");
        }

        html.append("            </div>\n");
        html.append("        </div>\n");

        // Issue list
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>🔍 Issue Details</h2>\n");

        if (result.getIssues().isEmpty()) {
            html.append("            <div style=\"padding: 20px; background: #e8f5e9; border-left: 4px solid #4caf50; border-radius: 8px;\">\n");
            html.append("                <h3 style=\"color: #2e7d32; margin: 0;\">✅ No Accessibility Issues Found</h3>\n");
            html.append("                <p style=\"margin: 10px 0 0; color: #388e3c;\">This page fully complies with WCAG 2.2 AA standards</p>\n");
            html.append("            </div>\n");
        } else {
            for (AccessibilityEngine.AccessibilityIssue issue : result.getIssues()) {
                html.append(generateIssueDetail(issue));
            }
        }

        html.append("        </div>\n");

        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        function toggle(el) {\n");
        html.append("            el.nextElementSibling.classList.toggle('show');\n");
        html.append("        }\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generate final aggregated HTML report
     */
    private static String generateFinalHtmlReport(
            List<PageResult> allResults,
            int totalIssues,
            int passedTests,
            double overallPassRate,
            Map<AccessibilityEngine.IssueSeverity, Integer> overallIssueCounts,
            List<AccessibilityEngine.AccessibilityIssue> allIssues,
            List<String> individualReports) {

        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\" />\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        html.append("    <title>Accessibility Test Aggregated Report</title>\n");
        html.append("    <style>\n");
        html.append(getReportStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"report\">\n");

        // Title
        html.append("        <h1 class=\"title\">Accessibility Test Aggregated Report</h1>\n");
        html.append("        <p class=\"subtitle\">WCAG 2.2 AA Standard</p>\n");
        html.append("        <p class=\"subtitle\">Generated Time: ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
              .append("</p>\n");

        // Overall statistics
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>📊 Overall Statistics</h2>\n");
        html.append("            <div class=\"stats\">\n");
        html.append("                <div class=\"stat-item\">\n");
        html.append("                    Pages Checked <div class=\"num\">").append(allResults.size()).append("</div>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"stat-item\">\n");
        html.append("                    Passed <div class=\"num pass\">").append(passedTests).append("</div>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"stat-item\">\n");
        html.append("                    Total Issues <div class=\"num\">").append(totalIssues).append("</div>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"stat-item\">\n");
        html.append("                    Pass Rate <div class=\"num\" style=\"color: ")
              .append(overallPassRate >= 80 ? "#2dc653" : overallPassRate >= 60 ? "#f9c74f" : "#e63946")
              .append(";\">").append(String.format("%.1f%%", overallPassRate)).append("</div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");

        // Issue severity statistics
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>⚠️ Issue Severity Statistics</h2>\n");
        html.append("            <div class=\"stats\">\n");

        for (AccessibilityEngine.IssueSeverity severity : new AccessibilityEngine.IssueSeverity[]{
                AccessibilityEngine.IssueSeverity.CRITICAL,
                AccessibilityEngine.IssueSeverity.HIGH,
                AccessibilityEngine.IssueSeverity.MEDIUM,
                AccessibilityEngine.IssueSeverity.LOW}) {
            int count = overallIssueCounts.getOrDefault(severity, 0);
            html.append("                <div class=\"stat-item\">\n");
            html.append("                    ").append(severity.getDisplayName());
            html.append("                    <div class=\"num\" style=\"color: ")
                  .append(severity.getColor()).append(";\">").append(count).append("</div>\n");
            html.append("                </div>\n");
        }

        html.append("            </div>\n");
        html.append("        </div>\n");

        // Page results
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>📋 Page Test Results</h2>\n");

        for (int i = 0; i < allResults.size(); i++) {
            PageResult result = allResults.get(i);
            String reportLink = i < individualReports.size() ? individualReports.get(i) : "";
            html.append(generateTestResultCard(result, reportLink));
        }

        html.append("        </div>\n");

        // All issues list (red highlighting)
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>🔍 All Issues Summary</h2>\n");

        if (allIssues.isEmpty()) {
            html.append("            <div style=\"padding: 20px; background: #e8f5e9; border-left: 4px solid #4caf50; border-radius: 8px;\">\n");
            html.append("                <h3 style=\"color: #2e7d32; margin: 0;\">✅ No Accessibility Issues Found</h3>\n");
            html.append("                <p style=\"margin: 10px 0 0; color: #388e3c;\">All pages fully comply with WCAG 2.2 AA standards</p>\n");
            html.append("            </div>\n");
        } else {
            // Group issues by page
            for (PageResult result : allResults) {
                if (!result.getIssues().isEmpty()) {
                    html.append("            <h3 class=\"page-title\">")
                          .append(result.getPageName()).append("</h3>\n");
                    
                    for (AccessibilityEngine.AccessibilityIssue issue : result.getIssues()) {
                        html.append(generateIssueDetail(issue));
                    }
                }
            }
        }

        html.append("        </div>\n");

        // Conclusion
        html.append("        <div class=\"conclusion\">\n");
        html.append("            <h3>📌 Test Conclusion</h3>\n");
        html.append("            <p>This test checked ")
              .append(allResults.size()).append(" pages, ")
              .append("found ").append(totalIssues).append(" accessibility issues, ")
              .append("including ").append(overallIssueCounts.getOrDefault(AccessibilityEngine.IssueSeverity.CRITICAL, 0))
              .append(" critical issues and ")
              .append(overallIssueCounts.getOrDefault(AccessibilityEngine.IssueSeverity.HIGH, 0))
              .append(" high priority issues.</p>\n");

        html.append("            <p>Pass Rate: <b>").append(String.format("%.1f%%", overallPassRate))
              .append("</b>, ");
        if (overallPassRate >= 80) {
            html.append("Accessibility is excellent");
        } else if (overallPassRate >= 60) {
            html.append("Accessibility is good, needs improvement");
        } else {
            html.append("Accessibility needs attention");
        }
        html.append("</p>\n");

        if (!allIssues.isEmpty()) {
            html.append("            <p>Main issues: <b>");
            List<String> summaryPoints = generateSummaryPoints(allIssues);
            html.append(String.join(", ", summaryPoints));
            html.append("</b>.</p>\n");
            html.append("            <p>Recommend prioritizing fix for Critical and High severity issues to ensure core accessibility features function properly.</p>\n");
        }

        html.append("        </div>\n");

        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        function toggle(el) {\n");
        html.append("            el.nextElementSibling.classList.toggle('show');\n");
        html.append("        }\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generate test result card
     */
    private static String generateTestResultCard(PageResult result, String reportLink) {
        StringBuilder card = new StringBuilder();
        card.append("            <div class=\"test-result-card\">\n");
        card.append("                <div class=\"test-result-head\" onclick=\"toggle(this)\">\n");
        card.append("                    <span>").append(result.getPageName()).append("</span>\n");
        card.append("                    <span class=\"badge\" style=\"background: ")
              .append(result.isPassed() ? "#4caf50" : "#f44336").append(";\">")
              .append(result.isPassed() ? "✓ PASS" : "✗ FAIL").append("</span>\n");
        card.append("                </div>\n");
        card.append("                <div class=\"test-result-body\">\n");
        card.append("                    <p><b>Page Name:</b> ").append(result.getPageName()).append("</p>\n");
        card.append("                    <p><b>Page URL:</b> ").append(result.getPageUrl()).append("</p>\n");
        card.append("                    <p><b>Page Title:</b> ").append(result.getPageTitle()).append("</p>\n");
        card.append("                    <p><b>Test Time:</b> ")
              .append(result.getTestTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        card.append("                    <p><b>Issues Found:</b> ").append(result.getTotalIssues()).append("</p>\n");
        card.append("                    <p><b>Pass Rate:</b> ").append(String.format("%.1f%%", result.getPassRate())).append("</p>\n");
        if (reportLink != null && !reportLink.isEmpty()) {
            card.append("                    <p><b>Detailed Report:</b> <a href=\"").append(reportLink)
                  .append("\" target=\"_blank\" style=\"color: #3498db; text-decoration: none;\">View Full Report</a></p>\n");
        }
        card.append("                </div>\n");
        card.append("            </div>\n");
        return card.toString();
    }

    /**
     * Generate issue detail (red highlighting)
     */
    private static String generateIssueDetail(AccessibilityEngine.AccessibilityIssue issue) {
        StringBuilder detail = new StringBuilder();
        detail.append("            <div class=\"issue-detail\">\n");
        detail.append("                <div class=\"issue-header\" onclick=\"toggle(this)\">\n");
        detail.append("                    <span class=\"issue-id\">").append(issue.getId()).append("</span>\n");
        detail.append("                    <span class=\"issue-severity\" style=\"color: ")
              .append(issue.getSeverity().getColor()).append(";\">")
              .append(issue.getSeverity().getDisplayName()).append("</span>\n");
        detail.append("                    <span class=\"issue-description\">").append(issue.getDescription());
        
        // Add hidden element indicator
        if (issue.isElementHidden()) {
            detail.append(" <span style=\"background:#ff9800;color:white;padding:2px 6px;border-radius:3px;font-size:11px;\">HIDDEN</span>");
        }
        
        detail.append("</span>\n");
        detail.append("                </div>\n");
        
        // Issue details (expandable section)
        detail.append("                <div class=\"issue-body\">\n");
        
        // Show warning for hidden elements
        if (issue.isElementHidden()) {
            detail.append("                    <div style=\"padding:12px;background:#fff3e0;border-left:4px solid #ff9800;margin-bottom:15px;border-radius:4px;\">\n");
            detail.append("                        <p style=\"margin:0;color:#e65100;font-weight:bold;\">⚠ Hidden/Invisible Element</p>\n");
            detail.append("                        <p style=\"margin:5px 0 0;color:#666;font-size:13px;\">This element is hidden or not visible on the page. Screenshot annotation may show an approximate location or visible parent element.</p>\n");
            detail.append("                    </div>\n");
        }
        
        if (issue.getCodeSnippet() != null && !issue.getCodeSnippet().isEmpty()) {
            // HTML code snippet - red highlight for problematic element
            detail.append("                    <div class=\"code-snippet\">\n");
            detail.append("                        <h4>Element HTML:</h4>\n");
            detail.append("                        <pre class=\"problematic-element\">").append(escapeHtml(issue.getCodeSnippet())).append("</pre>\n");
            detail.append("                    </div>\n");
        }
        
        // Display element selector (CSS selector to identify the problematic element)
        if (issue.getElementSelector() != null && !issue.getElementSelector().isEmpty()) {
            detail.append("                    <p><b>Element Selector:</b> <code>").append(escapeHtml(issue.getElementSelector())).append("</code></p>\n");
        }
        
        if (issue.getWcagCriteria() != null) {
            detail.append("                    <p><b>WCAG Standard:</b> ").append(issue.getWcagCriteria()).append("</p>\n");
        }
        
        if (issue.getFixSuggestion() != null) {
            detail.append("                    <p><b>Fix Suggestion:</b> ").append(issue.getFixSuggestion()).append("</p>\n");
        }
        
        // Display screenshot (if available)
        if (issue.getScreenshot() != null && issue.getScreenshot().length > 0) {
            String base64Image = java.util.Base64.getEncoder().encodeToString(issue.getScreenshot());
            detail.append("                    <div class=\"screenshot\">\n");
            detail.append("                        <h4>Screenshot:</h4>\n");
            detail.append("                        <img src=\"data:image/png;base64,").append(base64Image).append("\" alt=\"Issue Screenshot\" />\n");
            detail.append("                    </div>\n");
        }
        
        detail.append("                </div>\n");
        detail.append("            </div>\n");
        
        return detail.toString();
    }

    /**
     * Generate issue summary points
     */
    private static List<String> generateSummaryPoints(List<AccessibilityEngine.AccessibilityIssue> issues) {
        Map<String, Integer> categoryCounts = new HashMap<>();

        for (AccessibilityEngine.AccessibilityIssue issue : issues) {
            String category;
            String description = issue.getDescription().toLowerCase();

            if (description.contains("label") || description.contains("form")) {
                category = "Missing Form Labels";
            } else if (description.contains("alt") || description.contains("image")) {
                category = "Missing Image Alt Text";
            } else if (description.contains("contrast") || description.contains("color")) {
                category = "Insufficient Color Contrast";
            } else if (description.contains("focus") || description.contains("focus")) {
                category = "Keyboard Focus Issues";
            } else if (description.contains("title") || description.contains("heading")) {
                category = "Page Title Issues";
            } else {
                category = "Other Accessibility Issues";
            }

            categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
        }

        return categoryCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(4)
                .map(e -> e.getKey())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get report styles
     */
    private static String getReportStyles() {
        StringBuilder styles = new StringBuilder();
        styles.append("        * {\n");
        styles.append("            margin: 0;\n");
        styles.append("            padding: 0;\n");
        styles.append("            box-sizing: border-box;\n");
        styles.append("            font-family: \"Microsoft YaHei\", -apple-system, BlinkMacSystemFont, sans-serif;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        body {\n");
        styles.append("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        styles.append("            padding: 20px;\n");
        styles.append("            min-height: 100vh;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .report {\n");
        styles.append("            max-width: 1200px;\n");
        styles.append("            margin: 0 auto;\n");
        styles.append("            background: #fff;\n");
        styles.append("            padding: 40px;\n");
        styles.append("            border-radius: 16px;\n");
        styles.append("            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .title {\n");
        styles.append("            text-align: center;\n");
        styles.append("            margin-bottom: 10px;\n");
        styles.append("            font-size: 32px;\n");
        styles.append("            color: #2c3e50;\n");
        styles.append("            font-weight: 700;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .subtitle {\n");
        styles.append("            text-align: center;\n");
        styles.append("            color: #7f8c8d;\n");
        styles.append("            margin-bottom: 30px;\n");
        styles.append("            font-size: 14px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .section {\n");
        styles.append("            margin-bottom: 30px;\n");
        styles.append("            padding: 25px;\n");
        styles.append("            background: #f8f9fa;\n");
        styles.append("            border-radius: 12px;\n");
        styles.append("            border-left: 5px solid #3498db;\n");
        styles.append("            transition: all 0.3s ease;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .section:hover {\n");
        styles.append("            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);\n");
        styles.append("            transform: translateY(-2px);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .section h2 {\n");
        styles.append("            font-size: 20px;\n");
        styles.append("            margin-bottom: 20px;\n");
        styles.append("            color: #34495e;\n");
        styles.append("            font-weight: 600;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .stats {\n");
        styles.append("            display: flex;\n");
        styles.append("            gap: 20px;\n");
        styles.append("            flex-wrap: wrap;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .stat-item {\n");
        styles.append("            flex: 1;\n");
        styles.append("            min-width: 160px;\n");
        styles.append("            background: #fff;\n");
        styles.append("            padding: 20px;\n");
        styles.append("            border-radius: 10px;\n");
        styles.append("            text-align: center;\n");
        styles.append("            border: 1px solid #e9ecef;\n");
        styles.append("            transition: all 0.3s ease;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .stat-item:hover {\n");
        styles.append("            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);\n");
        styles.append("            transform: translateY(-2px);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .stat-item .num {\n");
        styles.append("            font-size: 28px;\n");
        styles.append("            font-weight: bold;\n");
        styles.append("            margin-top: 10px;\n");
        styles.append("            transition: transform 0.3s ease;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .stat-item:hover .num {\n");
        styles.append("            transform: scale(1.1);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .critical { color: #e74c3c; }\n");
        styles.append("        .high { color: #f39c12; }\n");
        styles.append("        .medium { color: #f1c40f; }\n");
        styles.append("        .low { color: #27ae60; }\n");
        styles.append("        .pass { color: #2ecc71; }\n");
        styles.append("\n");
        styles.append("        .result-status {\n");
        styles.append("            padding: 20px;\n");
        styles.append("            border-radius: 8px;\n");
        styles.append("            margin: 15px 0;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .result-status h3 {\n");
        styles.append("            margin: 0 0 10px 0;\n");
        styles.append("            font-size: 18px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .result-status p {\n");
        styles.append("            margin: 5px 0;\n");
        styles.append("            color: #555;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-card {\n");
        styles.append("            border: 1px solid #e9ecef;\n");
        styles.append("            border-radius: 10px;\n");
        styles.append("            margin-bottom: 15px;\n");
        styles.append("            overflow: hidden;\n");
        styles.append("            transition: all 0.3s ease;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-card:hover {\n");
        styles.append("            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-head {\n");
        styles.append("            padding: 16px 20px;\n");
        styles.append("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        styles.append("            color: white;\n");
        styles.append("            cursor: pointer;\n");
        styles.append("            display: flex;\n");
        styles.append("            justify-content: space-between;\n");
        styles.append("            align-items: center;\n");
        styles.append("            font-weight: 600;\n");
        styles.append("            font-size: 16px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .badge {\n");
        styles.append("            padding: 6px 14px;\n");
        styles.append("            border-radius: 20px;\n");
        styles.append("            color: white;\n");
        styles.append("            font-size: 12px;\n");
        styles.append("            font-weight: bold;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-body {\n");
        styles.append("            padding: 25px;\n");
        styles.append("            display: none;\n");
        styles.append("            border-top: 1px solid #e9ecef;\n");
        styles.append("            background: #fff;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-body.show {\n");
        styles.append("            display: block;\n");
        styles.append("            animation: slideDown 0.3s ease;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        @keyframes slideDown {\n");
        styles.append("            from { opacity: 0; transform: translateY(-10px); }\n");
        styles.append("            to { opacity: 1; transform: translateY(0); }\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-body p {\n");
        styles.append("            margin-bottom: 12px;\n");
        styles.append("            line-height: 1.8;\n");
        styles.append("            color: #555;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .test-result-body b {\n");
        styles.append("            color: #2c3e50;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .page-title {\n");
        styles.append("            font-size: 18px;\n");
        styles.append("            color: #2c3e50;\n");
        styles.append("            margin: 25px 0 15px 0;\n");
        styles.append("            padding-bottom: 8px;\n");
        styles.append("            border-bottom: 3px solid #3498db;\n");
        styles.append("            font-weight: 600;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-detail {\n");
        styles.append("            margin-bottom: 20px;\n");
        styles.append("            border: 1px solid #e9ecef;\n");
        styles.append("            border-radius: 10px;\n");
        styles.append("            overflow: hidden;\n");
        styles.append("            background: #fff;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-header {\n");
        styles.append("            padding: 15px 20px;\n");
        styles.append("            background: #f8f9fa;\n");
        styles.append("            border-bottom: 1px solid #e9ecef;\n");
        styles.append("            display: flex;\n");
        styles.append("            gap: 12px;\n");
        styles.append("            align-items: center;\n");
        styles.append("            cursor: pointer;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-id {\n");
        styles.append("            background: #6c757d;\n");
        styles.append("            color: white;\n");
        styles.append("            padding: 4px 10px;\n");
        styles.append("            border-radius: 4px;\n");
        styles.append("            font-size: 12px;\n");
        styles.append("            font-weight: bold;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-severity {\n");
        styles.append("            font-weight: bold;\n");
        styles.append("            font-size: 13px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-description {\n");
        styles.append("            flex: 1;\n");
        styles.append("            color: #495057;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-body {\n");
        styles.append("            padding: 20px;\n");
        styles.append("            display: none;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .issue-body.show {\n");
        styles.append("            display: block;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .code-snippet {\n");
        styles.append("            margin: 15px 0;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .code-snippet h4 {\n");
        styles.append("            color: #6c757d;\n");
        styles.append("            margin-bottom: 8px;\n");
        styles.append("            font-size: 14px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .problematic-element {\n");
        styles.append("            background: #f8d7da;\n");
        styles.append("            color: #721c24;\n");
        styles.append("            padding: 15px;\n");
        styles.append("            border-radius: 8px;\n");
        styles.append("            border-left: 4px solid #dc3545;\n");
        styles.append("            font-family: 'Courier New', monospace;\n");
        styles.append("            font-size: 13px;\n");
        styles.append("            line-height: 1.6;\n");
        styles.append("            overflow-x: auto;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .screenshot {\n");
        styles.append("            margin-top: 15px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .screenshot h4 {\n");
        styles.append("            color: #6c757d;\n");
        styles.append("            margin-bottom: 10px;\n");
        styles.append("            font-size: 14px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .screenshot img {\n");
        styles.append("            max-width: 100%;\n");
        styles.append("            border-radius: 8px;\n");
        styles.append("            border: 1px solid #dee2e6;\n");
        styles.append("            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .conclusion {\n");
        styles.append("            padding: 30px;\n");
        styles.append("            background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);\n");
        styles.append("            border-radius: 12px;\n");
        styles.append("            margin-top: 30px;\n");
        styles.append("            border-left: 5px solid #3498db;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .conclusion h3 {\n");
        styles.append("            color: #2c3e50;\n");
        styles.append("            margin-bottom: 15px;\n");
        styles.append("            font-size: 20px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .conclusion p {\n");
        styles.append("            line-height: 1.8;\n");
        styles.append("            color: #555;\n");
        styles.append("            margin-bottom: 12px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .conclusion b {\n");
        styles.append("            color: #3498db;\n");
        styles.append("        }\n");
        
        return styles.toString();
    }

    /**
     * HTML escape
     */
    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                 .replace("<", "&lt;")
                 .replace(">", "&gt;")
                 .replace("\"", "&quot;")
                 .replace("'", "&#39;");
    }

    /**
     * Save report to file
     */
    private static void saveReport(String content, String path) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            logger.info("Report saved to: {}", path);
        } catch (Exception e) {
            logger.error("Failed to save report to: {}", path, e);
        }
    }

    /**
     * Cleanup collector (call after test ends)
     */
    public static void cleanup() {
        results.remove();
        generatedReports.remove();
        testStartTime.remove();
        logger.info("AccessibilityScanner cleaned up");
    }
}

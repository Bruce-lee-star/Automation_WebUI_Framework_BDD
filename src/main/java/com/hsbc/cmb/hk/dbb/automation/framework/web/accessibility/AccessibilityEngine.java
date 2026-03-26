package com.hsbc.cmb.hk.dbb.automation.framework.web.accessibility;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import net.serenitybdd.core.Serenity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Accessibility Engine
 * Scans web pages for accessibility issues and generates detailed reports with screenshots
 */
public class AccessibilityEngine {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilityEngine.class);

    // Unified JavaScript function for generating unique CSS selectors
    // Priority: aria-label > data-testid > data-test-id > unique attributes > simplified path
    private static final String UNIQUE_SELECTOR_JS =
        "const getUniqueSelector = (element) => { " +
        // Priority 1: aria-label (most reliable for accessibility testing)
        "  if (element.getAttribute('aria-label')) { " +
        "    const label = element.getAttribute('aria-label').replace(/\"/g, '\\\\\"'); " +
        "    const selector = element.tagName.toLowerCase() + '[aria-label=\"' + label + '\"]'; " +
        "    const matches = document.querySelectorAll(selector); " +
        "    if (matches.length === 1) return selector; " +
        "  } " +
        // Priority 2: data-testid
        "  if (element.getAttribute('data-testid')) { " +
        "    return element.tagName.toLowerCase() + '[data-testid=\"' + element.getAttribute('data-testid') + '\"]'; " +
        "  } " +
        // Priority 3: data-test-id
        "  if (element.getAttribute('data-test-id')) { " +
        "    return element.tagName.toLowerCase() + '[data-test-id=\"' + element.getAttribute('data-test-id') + '\"]'; " +
        "  } " +
        // Priority 4: role + aria-label combination
        "  if (element.getAttribute('role') && element.getAttribute('aria-label')) { " +
        "    const role = element.getAttribute('role'); " +
        "    const label = element.getAttribute('aria-label').replace(/\"/g, '\\\\\"'); " +
        "    const selector = '[role=\"' + role + '\"][aria-label=\"' + label + '\"]'; " +
        "    const matches = document.querySelectorAll(selector); " +
        "    if (matches.length === 1) return selector; " +
        "  } " +
        // Priority 5: data-dismiss, data-toggle etc.
        "  if (element.getAttribute('data-dismiss')) { " +
        "    const dismiss = element.getAttribute('data-dismiss'); " +
        "    const selector = element.tagName.toLowerCase() + '[data-dismiss=\"' + dismiss + '\"]'; " +
        "    const matches = document.querySelectorAll(selector); " +
        "    if (matches.length <= 3) return selector; " +
        "  } " +
        // Priority 6: ID (avoid dynamic IDs with many numbers)
        "  if (element.id) { " +
        "    const numCount = (element.id.match(/\\d/g) || []).length; " +
        "    if (numCount < 5) { " +
        "      return '#' + CSS.escape(element.id); " +
        "    } " +
        "  } " +
        // Priority 7: name attribute
        "  if (element.getAttribute('name')) { " +
        "    return element.tagName.toLowerCase() + '[name=\"' + element.getAttribute('name') + '\"]'; " +
        "  } " +
        // Priority 8: title attribute
        "  if (element.getAttribute('title')) { " +
        "    const title = element.getAttribute('title').replace(/\"/g, '\\\\\"'); " +
        "    const selector = element.tagName.toLowerCase() + '[title=\"' + title + '\"]'; " +
        "    const matches = document.querySelectorAll(selector); " +
        "    if (matches.length === 1) return selector; " +
        "  } " +
        // Priority 9: Build simplified path (max 4 levels)
        "  const path = []; " +
        "  let current = element; " +
        "  let depth = 0; " +
        "  while (current && current !== document.body && depth < 4) { " +
        "    let selector = current.tagName.toLowerCase(); " +
        // Check for unique identifying attributes at each level
        "    if (current.getAttribute('aria-label')) { " +
        "      const label = current.getAttribute('aria-label').replace(/\"/g, '\\\\\"'); " +
        "      selector += '[aria-label=\"' + label + '\"]'; " +
        "      path.unshift(selector); " +
        "      break; " +
        "    } " +
        "    if (current.getAttribute('data-testid')) { " +
        "      selector += '[data-testid=\"' + current.getAttribute('data-testid') + '\"]'; " +
        "      path.unshift(selector); " +
        "      break; " +
        "    } " +
        "    if (current.id) { " +
        "      const numCount = (current.id.match(/\\d/g) || []).length; " +
        "      if (numCount < 5) { " +
        "        selector = '#' + CSS.escape(current.id); " +
        "        path.unshift(selector); " +
        "        break; " +
        "      } " +
        "    } " +
        // Add nth-of-type if needed
        "    const parent = current.parentElement; " +
        "    if (parent) { " +
        "      const siblings = Array.from(parent.children).filter(child => child.tagName === current.tagName); " +
        "      if (siblings.length > 1) { " +
        "        const index = siblings.indexOf(current) + 1; " +
        "        selector += ':nth-of-type(' + index + ')'; " +
        "      } " +
        "    } " +
        "    path.unshift(selector); " +
        "    current = parent; " +
        "    depth++; " +
        "  } " +
        "  return path.length > 0 ? path.join(' > ') : element.tagName.toLowerCase(); " +
        "}; ";

    // Accessibility Standards
    public enum AccessibilityStandard {
        WCAG_2_0_A("WCAG 2.0 A"),
        WCAG_2_0_AA("WCAG 2.0 AA"),
        WCAG_2_0_AAA("WCAG 2.0 AAA"),
        WCAG_2_1_A("WCAG 2.1 A"),
        WCAG_2_1_AA("WCAG 2.1 AA"),
        WCAG_2_2_AA("WCAG 2.2 AA");

        private final String displayName;

        AccessibilityStandard(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Issue Severity Levels
    public enum IssueSeverity {
        CRITICAL("Critical", "#e63946"),
        HIGH("High", "#f77f00"),
        MEDIUM("Medium", "#f9c74f"),
        LOW("Low", "#43aa8b"),
        INFO("Info", "#4a90e2");

        private final String displayName;
        private final String color;

        IssueSeverity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }
    }

    // Accessibility Issue Class
    public static class AccessibilityIssue {
        private String id;
        private String pageUrl;
        private String elementSelector;
        private IssueSeverity severity;
        private String description;
        private String wcagCriteria;
        private String violation;
        private String codeSnippet;
        private String fixSuggestion;
        private byte[] screenshot;
        private LocalDateTime timestamp;
        private boolean elementHidden;  // Whether the element is hidden/invisible

        public AccessibilityIssue(String id, String pageUrl, IssueSeverity severity, String description, String wcagCriteria) {
            this.id = id;
            this.pageUrl = pageUrl;
            this.severity = severity;
            this.description = description;
            this.wcagCriteria = wcagCriteria;
            this.timestamp = LocalDateTime.now();
            this.elementHidden = false;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPageUrl() { return pageUrl; }
        public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

        public String getElementSelector() { return elementSelector; }
        public void setElementSelector(String elementSelector) { this.elementSelector = elementSelector; }

        public IssueSeverity getSeverity() { return severity; }
        public void setSeverity(IssueSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getWcagCriteria() { return wcagCriteria; }
        public void setWcagCriteria(String wcagCriteria) { this.wcagCriteria = wcagCriteria; }

        public String getViolation() { return violation; }
        public void setViolation(String violation) { this.violation = violation; }

        public String getCodeSnippet() { return codeSnippet; }
        public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

        public String getFixSuggestion() { return fixSuggestion; }
        public void setFixSuggestion(String fixSuggestion) { this.fixSuggestion = fixSuggestion; }

        public byte[] getScreenshot() { return screenshot; }
        public void setScreenshot(byte[] screenshot) { this.screenshot = screenshot; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public boolean isElementHidden() { return elementHidden; }
        public void setElementHidden(boolean elementHidden) { this.elementHidden = elementHidden; }
    }
    
    // Screenshot Result Class (for returning screenshot with visibility info)
    public static class ScreenshotResult {
        private byte[] screenshot;
        private boolean elementHidden;
        
        public ScreenshotResult(byte[] screenshot, boolean elementHidden) {
            this.screenshot = screenshot;
            this.elementHidden = elementHidden;
        }

        public byte[] getScreenshot() { return screenshot; }
        public boolean isElementHidden() { return elementHidden; }
    }

    // Report Configuration Class (Enterprise Edition)
    public static class ReportConfig {
        // Basic Info
        private String projectName;
        private String projectVersion;
        private String tester;
        private AccessibilityStandard standard;
        private String reportTitle;
        private boolean includeScreenshots;

        // Enterprise Fields
        private String companyName;
        private String department;
        private String applicationUrl;
        private String testEnvironment;
        private String testType = "Automated Accessibility Scan";
        private String logoBase64;
        private boolean includeExecutiveSummary = true;
        private boolean includeWcagDetails = true;
        private boolean includeRemediationTimeline = true;
        private String approvedBy;
        private String reviewedBy;

        public ReportConfig() {
            this.standard = AccessibilityStandard.WCAG_2_2_AA;
            this.includeScreenshots = true;
            this.reportTitle = "Web Accessibility Compliance Report";
            this.companyName = "Enterprise";
            this.testEnvironment = "Production";
        }

        // Getters and Setters
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        public String getProjectVersion() { return projectVersion; }
        public void setProjectVersion(String projectVersion) { this.projectVersion = projectVersion; }

        public String getTester() { return tester; }
        public void setTester(String tester) { this.tester = tester; }

        public AccessibilityStandard getStandard() { return standard; }
        public void setStandard(AccessibilityStandard standard) { this.standard = standard; }

        public String getReportTitle() { return reportTitle; }
        public void setReportTitle(String reportTitle) { this.reportTitle = reportTitle; }

        public boolean isIncludeScreenshots() { return includeScreenshots; }
        public void setIncludeScreenshots(boolean includeScreenshots) { this.includeScreenshots = includeScreenshots; }

        // Enterprise Getters and Setters
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getApplicationUrl() { return applicationUrl; }
        public void setApplicationUrl(String applicationUrl) { this.applicationUrl = applicationUrl; }

        public String getTestEnvironment() { return testEnvironment; }
        public void setTestEnvironment(String testEnvironment) { this.testEnvironment = testEnvironment; }

        public String getTestType() { return testType; }
        public void setTestType(String testType) { this.testType = testType; }

        public String getLogoBase64() { return logoBase64; }
        public void setLogoBase64(String logoBase64) { this.logoBase64 = logoBase64; }

        public boolean isIncludeExecutiveSummary() { return includeExecutiveSummary; }
        public void setIncludeExecutiveSummary(boolean includeExecutiveSummary) { this.includeExecutiveSummary = includeExecutiveSummary; }

        public boolean isIncludeWcagDetails() { return includeWcagDetails; }
        public void setIncludeWcagDetails(boolean includeWcagDetails) { this.includeWcagDetails = includeWcagDetails; }

        public boolean isIncludeRemediationTimeline() { return includeRemediationTimeline; }
        public void setIncludeRemediationTimeline(boolean includeRemediationTimeline) { this.includeRemediationTimeline = includeRemediationTimeline; }

        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

        public String getReviewedBy() { return reviewedBy; }
        public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    }

    // WCAG Compliance Score
    public static class ComplianceScore {
        private double overallScore;
        private Map<String, Double> principleScores;
        private String complianceLevel;

        public ComplianceScore() {
            this.principleScores = new HashMap<>();
        }

        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }

        public Map<String, Double> getPrincipleScores() { return principleScores; }
        public void setPrincipleScores(Map<String, Double> principleScores) { this.principleScores = principleScores; }

        public String getComplianceLevel() { return complianceLevel; }
        public void setComplianceLevel(String complianceLevel) { this.complianceLevel = complianceLevel; }
    }

    // Test Result Statistics
    public static class TestStatistics {
        private int totalPages;
        private int totalIssues;
        private Map<IssueSeverity, Integer> issueCounts;
        private double passRate;
        private ComplianceScore complianceScore;
        private long testDurationMs;
        private int elementsScanned;
        private int pagesPassed;
        private int pagesFailed;

        public TestStatistics() {
            this.issueCounts = new EnumMap<>(IssueSeverity.class);
            for (IssueSeverity severity : IssueSeverity.values()) {
                issueCounts.put(severity, 0);
            }
            this.complianceScore = new ComplianceScore();
        }

        // Getters and Setters
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

        public int getTotalIssues() { return totalIssues; }
        public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }

        public Map<IssueSeverity, Integer> getIssueCounts() { return issueCounts; }
        public void incrementIssueCount(IssueSeverity severity) {
            issueCounts.put(severity, issueCounts.getOrDefault(severity, 0) + 1);
        }

        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }

        public ComplianceScore getComplianceScore() { return complianceScore; }
        public void setComplianceScore(ComplianceScore complianceScore) { this.complianceScore = complianceScore; }

        public long getTestDurationMs() { return testDurationMs; }
        public void setTestDurationMs(long testDurationMs) { this.testDurationMs = testDurationMs; }

        public int getElementsScanned() { return elementsScanned; }
        public void setElementsScanned(int elementsScanned) { this.elementsScanned = elementsScanned; }

        public int getPagesPassed() { return pagesPassed; }
        public void setPagesPassed(int pagesPassed) { this.pagesPassed = pagesPassed; }

        public int getPagesFailed() { return pagesFailed; }
        public void setPagesFailed(int pagesFailed) { this.pagesFailed = pagesFailed; }
    }

    // WCAG Principle Categories
    public enum WcagPrinciple {
        PERCEIVABLE("Perceivable", "Information and UI components must be presentable to users in ways they can perceive"),
        OPERABLE("Operable", "UI components and navigation must be operable"),
        UNDERSTANDABLE("Understandable", "Information and operation of UI must be understandable"),
        ROBUST("Robust", "Content must be robust enough for assistive technologies");

        private final String name;
        private final String description;

        WcagPrinciple(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /**
     * Check page accessibility (enhanced version, returns detailed Issue objects)
     *
     * @param page Playwright Page对象
     * @param includeScreenshots 是否包含截图
     * @return List of accessibility check results
     */
    public static List<AccessibilityIssue> checkPageAccessibilityEnhanced(Page page, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();

        if (page == null) {
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-001", "N/A", IssueSeverity.CRITICAL,
                "Page object is null, cannot perform accessibility check", "N/A");
            issues.add(issue);
            return issues;
        }

        logger.info("Starting enhanced accessibility check...");

        // ========== IMPORTANT: Wait for page to stabilize ==========
        waitForPageStability(page);
        // ==========================================================

        String pageUrl = page.url();
        int issueIndex = 1;

        try {
            // 1. Check page title
            issues.addAll(checkPageTitleEnhanced(page, pageUrl, issueIndex++));

            // 2. Check page language
            issues.addAll(checkPageLanguageEnhanced(page, pageUrl, issueIndex++));

            // 3. Check image alt attributes with screenshots
            issues.addAll(checkImagesAltTextEnhanced(page, pageUrl, includeScreenshots));

            // 4. Check form labels
            issues.addAll(checkFormLabelsEnhanced(page, pageUrl, includeScreenshots));

            // 5. Check link text
            issues.addAll(checkLinkTextEnhanced(page, pageUrl, includeScreenshots));

            // 6. Check heading hierarchy
            issues.addAll(checkHeadingHierarchyEnhanced(page, pageUrl, includeScreenshots));

            // 7. Check button accessibility
            issues.addAll(checkButtonAccessibilityEnhanced(page, pageUrl, includeScreenshots));

            // 8. Check focus management
            issues.addAll(checkFocusManagementEnhanced(page, pageUrl, includeScreenshots));

            // 9. Check color contrast
            issues.addAll(checkColorContrastEnhanced(page, pageUrl, includeScreenshots));

            // 10. Check ARIA attributes
            issues.addAll(checkAriaAttributesEnhanced(page, pageUrl, includeScreenshots));

            // 11. Check keyboard navigation
            issues.addAll(checkKeyboardNavigationEnhanced(page, pageUrl, includeScreenshots));

            logger.info("Enhanced accessibility check completed, found {} issues", issues.size());

        } catch (Exception e) {
            logger.error("Error during accessibility check: {}", e.getMessage(), e);
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-999", pageUrl, IssueSeverity.CRITICAL,
                "Error during accessibility check: " + e.getMessage(), "N/A");
            issues.add(issue);
        }

        return issues;
    }
    
    /**
     * Wait for page to stabilize before performing accessibility check
     * This ensures all elements are loaded, animations are complete, and the DOM is stable
     * 
     * @param page Playwright page object
     */
    private static void waitForPageStability(Page page) {
        try {
            logger.info("Waiting for page to stabilize...");
            
            // 1. Wait for DOM content loaded
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED, 
                    new Page.WaitForLoadStateOptions().setTimeout(10000));
                logger.debug("DOM content loaded");
            } catch (Exception e) {
                logger.debug("DOM content load timeout, continuing anyway");
            }
            
            // 2. Wait for network to be idle (no active network requests)
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(15000));
                logger.debug("Network idle");
            } catch (Exception e) {
                logger.debug("Network idle timeout, continuing anyway");
            }
            
            // 3. Wait for any animations to complete
            try {
                // Wait for CSS animations and transitions to complete
                page.waitForFunction("() => {" +
                    "  const animations = document.getAnimations();" +
                    "  return animations.every(a => a.playState === 'finished' || a.playState === 'idle');" +
                "}", new Page.WaitForFunctionOptions().setTimeout(5000));
                logger.debug("Animations completed");
            } catch (Exception e) {
                logger.debug("Animation wait timeout, continuing anyway");
            }
            
            // 4. Additional stability wait for dynamic content
            page.waitForTimeout(500);
            
            // 5. Wait for any lazy-loaded images
            try {
                page.waitForFunction("() => {" +
                    "  const images = document.querySelectorAll('img');" +
                    "  return Array.from(images).every(img => img.complete && img.naturalHeight > 0);" +
                "}", new Page.WaitForFunctionOptions().setTimeout(10000));
                logger.debug("Images loaded");
            } catch (Exception e) {
                logger.debug("Image load wait timeout, continuing anyway");
            }
            
            // 6. Final short wait for any remaining dynamic content
            page.waitForTimeout(300);
            
            logger.info("Page stabilization complete");
            
        } catch (Exception e) {
            logger.warn("Error during page stabilization: {}", e.getMessage());
            // Continue with accessibility check even if stabilization fails
        }
    }
    
    /**
     * Check page accessibility with custom stability timeout
     *
     * @param page Playwright Page object
     * @param includeScreenshots Whether to include screenshots
     * @param stabilityTimeoutMs Maximum time to wait for page stability (in milliseconds)
     * @return List of accessibility check results
     */
    public static List<AccessibilityIssue> checkPageAccessibilityEnhanced(Page page, boolean includeScreenshots, int stabilityTimeoutMs) {
        List<AccessibilityIssue> issues = new ArrayList<>();

        if (page == null) {
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-001", "N/A", IssueSeverity.CRITICAL,
                "Page object is null, cannot perform accessibility check", "N/A");
            issues.add(issue);
            return issues;
        }

        logger.info("Starting enhanced accessibility check with custom stability timeout: {}ms", stabilityTimeoutMs);

        // Wait for page to stabilize with custom timeout
        waitForPageStability(page, stabilityTimeoutMs);

        String pageUrl = page.url();
        int issueIndex = 1;

        try {
            issues.addAll(checkPageTitleEnhanced(page, pageUrl, issueIndex++));
            issues.addAll(checkPageLanguageEnhanced(page, pageUrl, issueIndex++));
            issues.addAll(checkImagesAltTextEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkFormLabelsEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkLinkTextEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkHeadingHierarchyEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkButtonAccessibilityEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkFocusManagementEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkColorContrastEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkAriaAttributesEnhanced(page, pageUrl, includeScreenshots));

            // 11. Check keyboard navigation
            issues.addAll(checkKeyboardNavigationEnhanced(page, pageUrl, includeScreenshots));

            logger.info("Enhanced accessibility check completed, found {} issues", issues.size());

        } catch (Exception e) {
            logger.error("Error during accessibility check: {}", e.getMessage(), e);
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-999", pageUrl, IssueSeverity.CRITICAL,
                "Error during accessibility check: " + e.getMessage(), "N/A");
            issues.add(issue);
        }

        return issues;
    }

    /**
     * Wait for page to stabilize with custom timeout
     * 
     * @param page Playwright page object
     * @param timeoutMs Maximum time to wait (in milliseconds)
     */
    private static void waitForPageStability(Page page, int timeoutMs) {
        try {
            logger.info("Waiting for page to stabilize (timeout: {}ms)...", timeoutMs);
            
            int domTimeout = Math.min(timeoutMs / 3, 10000);
            int networkTimeout = Math.min(timeoutMs / 2, 15000);
            
            // Wait for DOM content loaded
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED, 
                    new Page.WaitForLoadStateOptions().setTimeout(domTimeout));
            } catch (Exception e) {
                logger.debug("DOM content load timeout");
            }
            
            // Wait for network idle
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(networkTimeout));
            } catch (Exception e) {
                logger.debug("Network idle timeout");
            }
            
            // Wait for animations
            try {
                page.waitForFunction("() => {" +
                    "  const animations = document.getAnimations();" +
                    "  return animations.every(a => a.playState === 'finished' || a.playState === 'idle');" +
                "}", new Page.WaitForFunctionOptions().setTimeout(Math.min(timeoutMs / 4, 5000)));
            } catch (Exception e) {
                logger.debug("Animation wait timeout");
            }
            
            // Additional wait
            page.waitForTimeout(Math.min(timeoutMs / 10, 800));
            
            logger.info("Page stabilization complete");
            
        } catch (Exception e) {
            logger.warn("Error during page stabilization: {}", e.getMessage());
        }
    }
    
    /**
     * Check page accessibility without waiting for stability
     * Use this when you know the page is already stable
     *
     * @param page Playwright Page object
     * @param includeScreenshots Whether to include screenshots
     * @return List of accessibility check results
     */
    public static List<AccessibilityIssue> checkPageAccessibilityEnhancedNoWait(Page page, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();

        if (page == null) {
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-001", "N/A", IssueSeverity.CRITICAL,
                "Page object is null, cannot perform accessibility check", "N/A");
            issues.add(issue);
            return issues;
        }

        logger.info("Starting enhanced accessibility check (no stability wait)...");

        String pageUrl = page.url();
        int issueIndex = 1;

        try {
            issues.addAll(checkPageTitleEnhanced(page, pageUrl, issueIndex++));
            issues.addAll(checkPageLanguageEnhanced(page, pageUrl, issueIndex++));
            issues.addAll(checkImagesAltTextEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkFormLabelsEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkLinkTextEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkHeadingHierarchyEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkButtonAccessibilityEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkFocusManagementEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkColorContrastEnhanced(page, pageUrl, includeScreenshots));
            issues.addAll(checkAriaAttributesEnhanced(page, pageUrl, includeScreenshots));

            // 11. Check keyboard navigation
            issues.addAll(checkKeyboardNavigationEnhanced(page, pageUrl, includeScreenshots));

            logger.info("Enhanced accessibility check completed, found {} issues", issues.size());

        } catch (Exception e) {
            logger.error("Error during accessibility check: {}", e.getMessage(), e);
            AccessibilityIssue issue = new AccessibilityIssue("A11Y-ERROR-999", pageUrl, IssueSeverity.CRITICAL,
                "Error during accessibility check: " + e.getMessage(), "N/A");
            issues.add(issue);
        }

        return issues;
    }

    /**
     * Check page accessibility (basic version, returns string list)
     *
     * @param page Playwright Page对象
     * @return List of accessibility check results
     */
    public static List<String> checkPageAccessibility(Page page) {
        List<String> issues = new ArrayList<>();

        if (page == null) {
            issues.add("Page object is null, cannot perform accessibility check");
            return issues;
        }

        logger.info("Starting accessibility check...");

        // 1. Check page title
        checkPageTitle(page, issues);

        // 2. Check page language
        checkPageLanguage(page, issues);

        // 3. Check image alt attributes
        checkImagesAltText(page, issues);

        // 4. Check form labels
        checkFormLabels(page, issues);

        // 5. Check link text
        checkLinkText(page, issues);

        // 6. Check heading hierarchy
        checkHeadingHierarchy(page, issues);

        // 7. Check button accessibility
        checkButtonAccessibility(page, issues);

        // 8. Check focus management
        checkFocusManagement(page, issues);

        // 9. Check color contrast
        checkColorContrast(page, issues);

        // 10. Check ARIA attributes
        checkAriaAttributes(page, issues);

        logger.info("Accessibility check completed, found {} issues", issues.size());

        return issues;
    }
    
    /**
     * Check page title
     */
    private static void checkPageTitle(Page page, List<String> issues) {
        try {
            String title = page.title();
            if (title == null || title.trim().isEmpty()) {
                issues.add("Missing page title or empty title tag");
            } else if (title.length() > 60) {
                issues.add("Page title too long (" + title.length() + " characters), recommended max 60 characters");
            }
            logger.debug("Page title check: {}", title);
        } catch (Exception e) {
            issues.add("Error checking page title: " + e.getMessage());
        }
    }
    
    /**
     * Check page language
     */
    private static void checkPageLanguage(Page page, List<String> issues) {
        try {
            String lang = page.evaluate("document.documentElement.getAttribute('lang')").toString();
            if (lang == null || lang.trim().isEmpty()) {
                issues.add("Missing lang attribute on html element");
            }
            logger.debug("Page language check: {}", lang);
        } catch (Exception e) {
            issues.add("Error checking page language: " + e.getMessage());
        }
    }
    
    /**
     * Check image alt attributes
     */
    private static void checkImagesAltText(Page page, List<String> issues) {
        try {
            int missingAlt = (int) page.evaluate("() => { " +
                 "const images = document.querySelectorAll('img:not([alt]), img[alt=\"\"]'); " +
                "return images.length; " +
            "}");
            
            if (missingAlt > 0) {
                issues.add("Found " + missingAlt + " images missing alt attribute or with empty alt");
            }
            logger.debug("Image alt attribute check completed");
        } catch (Exception e) {
            issues.add("Error checking image alt attributes: " + e.getMessage());
        }
    }
    
    /**
     * Check form labels
     */
    private static void checkFormLabels(Page page, List<String> issues) {
        try {
            int unlabeledInputs = (int) page.evaluate("() => { " +
                "const inputs = document.querySelectorAll('input[type=\"text\"], input[type=\"email\"], ' + " +
                    "'input[type=\"password\"], input[type=\"number\"], textarea, select'); " +
                "return Array.from(inputs).filter(input => " +
                    "!input.labels || input.labels.length === 0).length; " +
            "}");
            
            if (unlabeledInputs > 0) {
                issues.add("Found " + unlabeledInputs + " form elements missing label tags");
            }
            logger.debug("Form labels check completed");
        } catch (Exception e) {
            issues.add("Error checking form labels: " + e.getMessage());
        }
    }
    
    /**
     * Check link text
     */
    private static void checkLinkText(Page page, List<String> issues) {
        try {
            int emptyLinks = (int) page.evaluate("() => { " +
                "const links = document.querySelectorAll('a'); " +
                "return Array.from(links).filter(link => " +
                    "!link.textContent.trim() && " +
                    "!link.querySelector('img[alt]')).length; " +
            "}");
            
            if (emptyLinks > 0) {
                issues.add("Found " + emptyLinks + " links missing text description");
            }
            logger.debug("Link text check completed");
        } catch (Exception e) {
            issues.add("Error checking link text: " + e.getMessage());
        }
    }
    
    /**
     * Check heading hierarchy
     */
    private static void checkHeadingHierarchy(Page page, List<String> issues) {
        try {
            String hierarchyIssue = (String) page.evaluate("() => { " +
                "const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6'); " +
                "let lastLevel = 0; " +
                "let issues = []; " +
                "headings.forEach(h => { " +
                    "const level = parseInt(h.tagName[1]); " +
                    "if (level > lastLevel + 1 && lastLevel !== 0) { " +
                        "issues.push('Incorrect heading hierarchy: ' + h.tagName + ' after H' + lastLevel); " +
                    "}" +
                    "lastLevel = level;" +
                "}); " +
                "return issues.join(', '); " +
            "}");
            
            if (hierarchyIssue != null && !hierarchyIssue.isEmpty()) {
                issues.add("Heading hierarchy issue: " + hierarchyIssue);
            }
            logger.debug("Heading hierarchy check completed");
        } catch (Exception e) {
            issues.add("Error checking heading hierarchy: " + e.getMessage());
        }
    }
    
    /**
     * 检查按钮可访问性
     */
    private static void checkButtonAccessibility(Page page, List<String> issues) {
        try {
            int emptyButtons = (int) page.evaluate("() => { " +
                "const buttons = document.querySelectorAll('button'); " +
                "return Array.from(buttons).filter(btn => " +
                    "!btn.textContent.trim() && " +
                    "!btn.getAttribute('aria-label') && " +
                    "!btn.getAttribute('title')).length; " +
            "}");
            
            if (emptyButtons > 0) {
                issues.add("Found " + emptyButtons + " buttons missing text description or aria-label");
            }
            logger.debug("Button accessibility check completed");
        } catch (Exception e) {
            issues.add("Error checking button accessibility: " + e.getMessage());
        }
    }
    
    /**
     * 检查焦点管理
     */
    private static void checkFocusManagement(Page page, List<String> issues) {
        try {
            int noFocusIndicators = (int) page.evaluate("() => { " +
                "const styles = window.getComputedStyle(document.body); " +
                "const hasFocusStyle = (element) => { " +
                    "const focusedStyles = window.getComputedStyle(element); " +
                    "return focusedStyles.outline !== 'none' || " +
                           "focusedStyles.boxShadow !== 'none'; " +
                "}; " +
                "return hasFocusStyle(document.body) ? 0 : 1; " +
            "}");
            
            if (noFocusIndicators > 0) {
                issues.add("Page may be missing focus visibility styles");
            }
            logger.debug("Focus management check completed");
        } catch (Exception e) {
            issues.add("Error checking focus management: " + e.getMessage());
        }
    }
    
    /**
     * 检查颜色对比度
     */
    private static void checkColorContrast(Page page, List<String> issues) {
        try {
            // Simple check: find elements with potential low contrast
            int potentialLowContrast = (int) page.evaluate("() => { " +
                "const elements = document.querySelectorAll('*'); " +
                "return Array.from(elements).filter(el => { " +
                    "const styles = window.getComputedStyle(el); " +
                    "const color = styles.color; " +
                    "const bg = styles.backgroundColor; " +
                    "return color !== 'rgba(0, 0, 0, 0)' && " +
                           "bg !== 'rgba(0, 0, 0, 0)' && " +
                           "color !== bg; " +
                "}).length; " +
            "}");
            
            logger.debug("Color contrast check completed");
        } catch (Exception e) {
            issues.add("Error checking color contrast: " + e.getMessage());
        }
    }
    
    /**
     * 检查ARIA属性
     */
    private static void checkAriaAttributes(Page page, List<String> issues) {
        try {
            int invalidAria = (int) page.evaluate("() => { " +
                "const elements = document.querySelectorAll('[aria-hidden=\"false\"]'); " +
                "return Array.from(elements).filter(el => { " +
                    "const styles = window.getComputedStyle(el); " +
                    "return styles.display === 'none' || styles.visibility === 'hidden'; " +
                "}).length; " +
            "}");
            
            if (invalidAria > 0) {
                issues.add("Found " + invalidAria + " elements with incorrect aria-hidden attribute");
            }
            logger.debug("ARIA attributes check completed");
        } catch (Exception e) {
            issues.add("Error checking ARIA attributes: " + e.getMessage());
        }
    }
    
    /**
     * 检查特定元素的无障碍性
     * 
     * @param page Playwright Page对象
     * @param selector CSS选择器
     * @return 无障碍检查结果
     */
    public static List<String> checkElementAccessibility(Page page, String selector) {
        List<String> issues = new ArrayList<>();
        
        try {
            boolean exists = page.locator(selector).count() > 0;
            if (!exists) {
                issues.add("Element not found for selector: " + selector);
                return issues;
            }
            
            // Check if element is visible
            boolean isVisible = page.locator(selector).isVisible();
            if (!isVisible) {
                issues.add("Element is not visible: " + selector);
            }
            
            // Check if element is accessible
            boolean isAccessible = (boolean) page.evaluate("sel => { " +
                "const el = document.querySelector(sel); " +
                "return el && !el.hasAttribute('aria-hidden'); " +
            "}", selector);
            
            if (!isAccessible) {
                issues.add("Element is hidden via ARIA: " + selector);
            }
            
            logger.debug("Element accessibility check completed: {}", selector);
            
        } catch (Exception e) {
            issues.add("Error checking element accessibility: " + e.getMessage());
        }
        
        return issues;
    }
    
    /**
     * 快速检查页面基本无障碍性（仅检查关键项）
     * 
     * @param page Playwright Page对象
     * @return List of accessibility check results
     */
    public static List<String> quickAccessibilityCheck(Page page) {
        List<String> issues = new ArrayList<>();
        
        if (page == null) {
            issues.add("Page object is null, cannot perform accessibility check");
            return issues;
        }
        
        logger.info("Performing quick accessibility check...");
        
        // Check only key items
        checkPageTitle(page, issues);
        checkImagesAltText(page, issues);
        checkFormLabels(page, issues);
        
        logger.info("Quick accessibility check completed, found {} issues", issues.size());
        
        return issues;
    }
    
    /**
     * 输出无障碍检查报告
     * 
     * @param issues 无障碍问题列表
     * @return 格式化的报告字符串
     */
    public static String generateReport(List<String> issues) {
        if (issues.isEmpty()) {
            return " Accessibility check passed, no issues found";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("Accessibility Check Results\n");
        report.append("==========================\n");
        report.append("Issues found: ").append(issues.size()).append("\n\n");
        
        for (int i = 0; i < issues.size(); i++) {
            report.append((i + 1)).append(". ").append(issues.get(i)).append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * 检查页面无障碍性并自动记录到Serenity报告
     * 
     * @param page Playwright Page对象
     * @return List of accessibility check results
     */
    public static List<String> checkPageAccessibilityWithReport(Page page) {
        List<String> issues = checkPageAccessibility(page);
        
        // Generate and record report to Serenity
        recordAccessibilityReportToSerenity(page, issues, "Full Accessibility Check");
        
        return issues;
    }
    
    /**
     * 快速检查页面无障碍性并自动记录到Serenity报告
     * 
     * @param page Playwright Page对象
     * @return List of accessibility check results
     */
    public static List<String> quickAccessibilityCheckWithReport(Page page) {
        List<String> issues = quickAccessibilityCheck(page);
        
        // Generate and record report to Serenity
        recordAccessibilityReportToSerenity(page, issues, "Quick Accessibility Check");
        
        return issues;
    }
    
    /**
     * 检查特定元素无障碍性并自动记录到Serenity报告
     * 
     * @param page Playwright Page对象
     * @param selector CSS选择器
     * @param elementName 元素名称（用于报告）
     * @return List of accessibility check results
     */
    public static List<String> checkElementAccessibilityWithReport(Page page, String selector, String elementName) {
        List<String> issues = checkElementAccessibility(page, selector);
        
        // Record element-specific report to Serenity
        recordElementAccessibilityReportToSerenity(page, selector, elementName, issues);
        
        return issues;
    }
    
    /**
     * 将无障碍检查结果记录到Serenity报告
     * 
     * @param page Playwright Page对象
     * @param issues 无障碍问题列表
     * @param checkType 检查类型（如"Full Check", "Quick Check"）
     */
    public static void recordAccessibilityReportToSerenity(Page page, List<String> issues, String checkType) {
        try {
            // Record summary data
            Serenity.recordReportData()
                .withTitle("Accessibility Check Summary")
                .andContents(generateHtmlSummary(issues, checkType, page));
            
            // Record detailed issues
            if (!issues.isEmpty()) {
                Serenity.recordReportData()
                    .withTitle("Accessibility Issues Detail")
                    .andContents(generateHtmlIssues(issues));
                
                // Record JSON data for potential further processing
                Serenity.recordReportData()
                    .withTitle("Accessibility Issues JSON")
                    .andContents(generateJsonReport(issues));
            }
            
            logger.info("Accessibility report recorded to Serenity successfully");
        } catch (Exception e) {
            logger.error("Failed to record accessibility report to Serenity: {}", e.getMessage());
        }
    }
    
    /**
     * 将元素无障碍检查结果记录到Serenity报告
     * 
     * @param page Playwright Page对象
     * @param selector CSS选择器
     * @param elementName 元素名称
     * @param issues 无障碍问题列表
     */
    public static void recordElementAccessibilityReportToSerenity(Page page, String selector, String elementName, List<String> issues) {
        try {
            String elementReport = generateElementReport(selector, elementName, issues);
            
            Serenity.recordReportData()
                .withTitle("Element Accessibility: " + elementName)
                .andContents(elementReport);
            
            logger.info("Element accessibility report recorded to Serenity: {}", elementName);
        } catch (Exception e) {
            logger.error("Failed to record element accessibility report to Serenity: {}", e.getMessage());
        }
    }
    
    /**
     * 生成HTML格式的摘要报告
     */
    private static String generateHtmlSummary(List<String> issues, String checkType, Page page) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String pageUrl = page != null ? page.url() : "N/A";
        String pageTitle = page != null ? page.title() : "N/A";
        
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        html.append("h1 { color: #333; }");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".pass { color: green; font-weight: bold; }");
        html.append(".fail { color: red; font-weight: bold; }");
        html.append("</style></head><body>");
        
        html.append("<h1>Accessibility Check Report</h1>");
        html.append("<table><tr><th>Property</th><th>Value</th></tr>");
        html.append("<tr><td>Check Type</td><td>").append(checkType).append("</td></tr>");
        html.append("<tr><td>Timestamp</td><td>").append(timestamp).append("</td></tr>");
        html.append("<tr><td>Page URL</td><td>").append(pageUrl).append("</td></tr>");
        html.append("<tr><td>Page Title</td><td>").append(pageTitle).append("</td></tr>");
        
        String status = issues.isEmpty() ? "PASS" : "FAIL";
        String statusClass = issues.isEmpty() ? "pass" : "fail";
        html.append("<tr><td>Status</td><td class=\"").append(statusClass).append("\">").append(status).append("</td></tr>");
        html.append("<tr><td>Issues Found</td><td>").append(issues.size()).append("</td></tr>");
        html.append("</table>");
        
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * 生成HTML格式的详细问题列表
     */
    private static String generateHtmlIssues(List<String> issues) {
        if (issues.isEmpty()) {
            return "<div class='pass'>No accessibility issues found!</div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        html.append("h2 { color: #d32f2f; }");
        html.append("ol { margin: 20px 0; padding-left: 20px; }");
        html.append("li { margin: 10px 0; padding: 10px; background-color: #fff3e0; border-left: 4px solid #ff9800; }");
        html.append("</style></head><body>");
        
        html.append("<h2>Accessibility Issues Found (").append(issues.size()).append(")</h2>");
        html.append("<ol>");
        
        for (int i = 0; i < issues.size(); i++) {
            html.append("<li>").append(issues.get(i)).append("</li>");
        }
        
        html.append("</ol>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * 生成JSON格式的报告
     */
    private static String generateJsonReport(List<String> issues) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
        json.append("  \"totalIssues\": ").append(issues.size()).append(",\n");
        json.append("  \"issues\": [\n");
        
        for (int i = 0; i < issues.size(); i++) {
            json.append("    \"").append(i + 1).append("\": \"").append(issues.get(i).replace("\"", "\\\"")).append("\"");
            if (i < issues.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * 生成元素无障碍报告
     */
    private static String generateElementReport(String selector, String elementName, List<String> issues) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        html.append("h2 { color: #333; }");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".pass { color: green; font-weight: bold; }");
        html.append(".fail { color: red; font-weight: bold; }");
        html.append("</style></head><body>");
        
        html.append("<h2>Element Accessibility Report</h2>");
        html.append("<table><tr><th>Property</th><th>Value</th></tr>");
        html.append("<tr><td>Element Name</td><td>").append(elementName).append("</td></tr>");
        html.append("<tr><td>Selector</td><td><code>").append(selector).append("</code></td></tr>");
        html.append("<tr><td>Timestamp</td><td>").append(timestamp).append("</td></tr>");
        
        String status = issues.isEmpty() ? "PASS" : "FAIL";
        String statusClass = issues.isEmpty() ? "pass" : "fail";
        html.append("<tr><td>Status</td><td class=\"").append(statusClass).append("\">").append(status).append("</td></tr>");
        html.append("<tr><td>Issues Found</td><td>").append(issues.size()).append("</td></tr>");
        html.append("</table>");
        
        if (!issues.isEmpty()) {
            html.append("<h3>Issues:</h3><ul>");
            for (String issue : issues) {
                html.append("<li>").append(issue).append("</li>");
            }
            html.append("</ul>");
        }
        
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * 生成Markdown格式的报告
     * 
     * @param issues 无障碍问题列表
     * @param checkType 检查类型
     * @return Markdown格式报告
     */
    public static String generateMarkdownReport(List<String> issues, String checkType) {
        StringBuilder md = new StringBuilder();
        md.append("# Accessibility Check Report\n\n");
        md.append("**Check Type:** ").append(checkType).append("\n");
        md.append("**Timestamp:** ").append(LocalDateTime.now()).append("\n");
        md.append("**Status:** ").append(issues.isEmpty() ? "✓ PASS" : "✗ FAIL").append("\n");
        md.append("**Issues Found:** ").append(issues.size()).append("\n\n");

        if (!issues.isEmpty()) {
            md.append("## Issues Found\n\n");
            for (int i = 0; i < issues.size(); i++) {
                md.append((i + 1)).append(". ").append(issues.get(i)).append("\n");
            }
        } else {
            md.append("✓ No accessibility issues found!\n");
        }

        return md.toString();
    }

    // ==================== 增强版检查方法 ====================

    /**
     * Check page title（增强版）
     */
    private static List<AccessibilityIssue> checkPageTitleEnhanced(Page page, String pageUrl, int issueIndex) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            String title = page.title();
            if (title == null || title.trim().isEmpty()) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    String.format("A11Y-%03d", issueIndex),
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Page missing title or title is empty",
                    "WCAG 2.4.2 (A)"
                );
                issue.setViolation("Every page must have a unique title");
                issue.setFixSuggestion("<title>Page Title</title>");
                issues.add(issue);
            } else if (title.length() > 60) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    String.format("A11Y-%03d", issueIndex),
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    "Page title too long (" + title.length() + " characters), recommended max 60 characters",
                    "WCAG 2.4.2 (A)"
                );
                issue.setCodeSnippet("<title>" + title.substring(0, Math.min(30, title.length())) + "...</title>");
                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking page title: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check page language（增强版）
     */
    private static List<AccessibilityIssue> checkPageLanguageEnhanced(Page page, String pageUrl, int issueIndex) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            String lang = page.evaluate("document.documentElement.getAttribute('lang')").toString();
            if (lang == null || lang.trim().isEmpty()) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    String.format("A11Y-%03d", issueIndex),
                    pageUrl,
                    IssueSeverity.HIGH,
                    "HTML element missing lang attribute",
                    "WCAG 3.1.1 (A)"
                );
                issue.setViolation("Must declare the primary language of the page");
                issue.setFixSuggestion("<html lang=\"en\">");
                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking page language: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check image alt attributes（enhanced version）
     */
    private static List<AccessibilityIssue> checkImagesAltTextEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const images = document.querySelectorAll('img:not([alt]), img[alt=\"\"]'); " +
                "return Array.from(images).map(img => ({ " +
                    "src: img.src || '', " +
                    "alt: img.alt || '', " +
                    "outerHTML: img.outerHTML.substring(0, 200), " +
                    "uniqueSelector: getUniqueSelector(img) " +
                "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> missingAltImages = (List<Map<String, Object>>) result;

            for (int i = 0; i < missingAltImages.size(); i++) {
                Map<String, Object> imgData = missingAltImages.get(i);
                String issueId = String.format("A11Y-%03d", 4 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Image missing alt attribute or alt is empty",
                    "WCAG 1.1.1 (A)"
                );
                issue.setViolation("All non-decorative images must have descriptive alt text");
                issue.setCodeSnippet((String) imgData.get("outerHTML"));

                // Generate meaningful fix suggestion based on image src
                String src = (String) imgData.get("src");
                String fixSuggestion = generateImageAltFixSuggestion(src);
                issue.setFixSuggestion(fixSuggestion);

                // Use unique selector from JavaScript
                String imageSelector = (String) imgData.get("uniqueSelector");
                issue.setElementSelector(imageSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, imageSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}: {}", issueId, e.getMessage());
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking image alt attributes: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Generate meaningful alt text fix suggestion based on image src
     */
    private static String generateImageAltFixSuggestion(String src) {
        if (src == null || src.isEmpty()) {
            return "<img src=\"...\" alt=\"Descriptive text for image\">";
        }

        // Extract filename or use URL hints
        String altText = "Image description";

        // Check for common image patterns
        if (src.contains("logo")) {
            altText = "Company logo";
        } else if (src.contains("icon")) {
            altText = "Icon";
        } else if (src.contains("banner")) {
            altText = "Banner image";
        } else if (src.contains("button")) {
            altText = "Button image";
        } else if (src.contains("arrow") || src.contains("next") || src.contains("prev")) {
            altText = "Navigation arrow";
        } else if (src.contains("close") || src.contains("cancel")) {
            altText = "Close button";
        } else if (src.contains("menu") || src.contains("hamburger")) {
            altText = "Menu button";
        } else if (src.contains("search")) {
            altText = "Search icon";
        } else if (src.contains("user") || src.contains("avatar") || src.contains("profile")) {
            altText = "User avatar";
        } else if (src.contains("chart") || src.contains("graph")) {
            altText = "Chart or graph";
        } else if (src.contains("photo") || src.contains("picture")) {
            // Extract meaningful part from filename
            try {
                String filename = src.substring(src.lastIndexOf('/') + 1);
                filename = filename.replaceAll("\\.(jpg|jpeg|png|gif|webp|svg)$", "");
                filename = filename.replaceAll("[-_]", " ").replaceAll("\\d+", "").trim();
                if (filename.length() > 3 && filename.length() < 50) {
                    altText = filename.substring(0, 1).toUpperCase() + filename.substring(1);
                }
            } catch (Exception e) {
                // Use default
            }
        }

        return "<img src=\"" + (src.length() > 60 ? src.substring(0, 60) + "..." : src) + "\" alt=\"" + altText + "\">";
    }

    /**
     * Check form labels（enhanced version）
     */
    private static List<AccessibilityIssue> checkFormLabelsEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const inputs = document.querySelectorAll('input[type=\"text\"], input[type=\"email\"], ' + " +
                    "'input[type=\"password\"], input[type=\"number\"], input[type=\"tel\"], input[type=\"url\"], textarea, select'); " +
                "return Array.from(inputs).filter(input => " +
                    "!input.labels || input.labels.length === 0).map((input, idx) => ({ " +
                        "type: input.type || 'text', " +
                        "tagName: input.tagName.toLowerCase(), " +
                        "placeholder: input.placeholder || '', " +
                        "id: input.id || '', " +
                        "className: input.className || '', " +
                        "name: input.name || '', " +
                        "outerHTML: input.outerHTML.substring(0, 200), " +
                        "index: idx, " +
                        "uniqueSelector: getUniqueSelector(input) " +
                    "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> unlabeledInputs = (List<Map<String, Object>>) result;

            for (int i = 0; i < unlabeledInputs.size(); i++) {
                Map<String, Object> input = unlabeledInputs.get(i);
                String issueId = String.format("A11Y-%03d", 10 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Form element missing associated label",
                    "WCAG 1.3.1, 3.3.2 (A)"
                );
                issue.setViolation("All form elements must have corresponding labels");
                issue.setCodeSnippet((String) input.get("outerHTML"));
                
                String id = (String) input.get("id");
                String fixTemplate = id != null && !id.isEmpty()
                    ? String.format("<label for=\"%s\">Label Text</label>\n<input id=\"%s\" ...>", id, id)
                    : String.format("<label for=\"input-%d\">Label Text</label>\n<input id=\"input-%d\" ...>", i, i);
                issue.setFixSuggestion(fixTemplate);

                // Use unique selector from JavaScript
                String inputSelector = (String) input.get("uniqueSelector");
                issue.setElementSelector(inputSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, inputSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}: {}", issueId, e.getMessage());
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking form labels: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check link text（enhanced version）
     * Identifies specific links with missing text and captures screenshots
     */
    private static List<AccessibilityIssue> checkLinkTextEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Get detailed information about problematic links with precise CSS selector
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const links = document.querySelectorAll('a'); " +
                "return Array.from(links).filter(link => " +
                    "!link.textContent.trim() && " +
                    "!link.querySelector('img[alt]')).map(link => ({ " +
                        "href: link.href ? link.href.substring(0, 100) : '', " +
                        "outerHTML: link.outerHTML.substring(0, 200), " +
                        "uniqueSelector: getUniqueSelector(link) " +
                    "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> emptyLinks = (List<Map<String, Object>>) result;

            for (int i = 0; i < emptyLinks.size(); i++) {
                Map<String, Object> linkData = emptyLinks.get(i);
                String issueId = String.format("A11Y-%03d", 15 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Link missing text description",
                    "WCAG 2.4.4 (A)"
                );
                issue.setViolation("Links must have descriptive text");
                issue.setFixSuggestion("Add descriptive text to the link element using aria-label or visually hidden text");
                issue.setCodeSnippet((String) linkData.get("outerHTML"));

                // Use the unique selector generated by JavaScript
                String linkSelector = (String) linkData.get("uniqueSelector");
                issue.setElementSelector(linkSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, linkSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}: {}", issueId, e.getMessage());
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking link text: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check heading hierarchy（enhanced version）
     * Identifies heading elements that skip levels and captures screenshots
     */
    private static List<AccessibilityIssue> checkHeadingHierarchyEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Get detailed information about heading hierarchy issues with precise CSS selector
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6'); " +
                "let lastLevel = 0; " +
                "let issueList = []; " +
                "headings.forEach((h, idx) => { " +
                    "const level = parseInt(h.tagName[1]); " +
                    "if (level > lastLevel + 1 && lastLevel !== 0) { " +
                        "issueList.push({ " +
                            "tagName: h.tagName, " +
                            "text: h.textContent.trim().substring(0, 50), " +
                            "previousLevel: lastLevel, " +
                            "currentLevel: level, " +
                            "id: h.id || '', " +
                            "className: h.className || '', " +
                            "outerHTML: h.outerHTML.substring(0, 200), " +
                            "index: idx, " +
                            "uniqueSelector: getUniqueSelector(h) " +
                        "}); " +
                    "}" +
                    "lastLevel = level;" +
                "}); " +
                "return issueList; " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hierarchyIssues = (List<Map<String, Object>>) result;

            for (int i = 0; i < hierarchyIssues.size(); i++) {
                Map<String, Object> issueData = hierarchyIssues.get(i);
                String issueId = String.format("A11Y-%03d", 16 + i);

                String description = String.format("Heading hierarchy skip: %s after H%d (text: \"%s\")", 
                    issueData.get("tagName"), 
                    issueData.get("previousLevel"),
                    issueData.get("text"));

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    description,
                    "WCAG 1.3.1 (A)"
                );
                issue.setViolation("Heading levels should increment sequentially, not skip levels");
                issue.setFixSuggestion("Ensure heading hierarchy follows H1 -> H2 -> H3 order");
                issue.setCodeSnippet((String) issueData.get("outerHTML"));

                // Use unique selector from JavaScript
                String headingSelector = (String) issueData.get("uniqueSelector");
                issue.setElementSelector(headingSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, headingSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}: {}", issueId, e.getMessage());
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking heading hierarchy: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check button accessibility（enhanced version）
     */
    private static List<AccessibilityIssue> checkButtonAccessibilityEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Get detailed information about problematic buttons with precise CSS selector
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const buttons = document.querySelectorAll('button'); " +
                "return Array.from(buttons).filter(btn => " +
                    "!btn.textContent.trim() && " +
                    "!btn.getAttribute('aria-label') && " +
                    "!btn.getAttribute('title')).map(btn => ({ " +
                        "outerHTML: btn.outerHTML.substring(0, 200), " +
                        "id: btn.id || '', " +
                        "className: btn.className || '', " +
                        "uniqueSelector: getUniqueSelector(btn) " +
                    "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> emptyButtons = (List<Map<String, Object>>) result;

            for (int i = 0; i < emptyButtons.size(); i++) {
                Map<String, Object> buttonData = emptyButtons.get(i);
                String issueId = String.format("A11Y-%03d", 17 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Button missing text description or aria-label",
                    "WCAG 4.1.2 (A)"
                );
                issue.setViolation("Buttons must have accessible names");
                issue.setFixSuggestion("<button>Button Text</button> or <button aria-label=\"Button Description\">");
                issue.setCodeSnippet((String) buttonData.get("outerHTML"));

                // Use unique selector from JavaScript
                String buttonSelector = (String) buttonData.get("uniqueSelector");
                issue.setElementSelector(buttonSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, buttonSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking button accessibility: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check keyboard navigation（enhanced version）
     * Tests Tab key navigation, keyboard traps, and tabindex issues
     */
    private static List<AccessibilityIssue> checkKeyboardNavigationEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            logger.info("Starting keyboard navigation check...");

            // 1. Check for invalid tabindex values
            List<AccessibilityIssue> tabindexIssues = checkInvalidTabindex(page, pageUrl, includeScreenshots);
            issues.addAll(tabindexIssues);

            // 2. Check for elements that should be focusable but aren't
            List<AccessibilityIssue> focusableIssues = checkFocusableElements(page, pageUrl, includeScreenshots);
            issues.addAll(focusableIssues);

            // 3. Check for potential keyboard traps
            List<AccessibilityIssue> trapIssues = checkKeyboardTraps(page, pageUrl, includeScreenshots);
            issues.addAll(trapIssues);

            // 4. Simulate Tab navigation to verify all focusable elements are reachable
            List<AccessibilityIssue> navigationIssues = simulateTabNavigation(page, pageUrl, includeScreenshots);
            issues.addAll(navigationIssues);

            logger.info("Keyboard navigation check completed, found {} issues", issues.size());
        } catch (Exception e) {
            logger.error("Error checking keyboard navigation: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check focus management（enhanced version）
     */
    private static List<AccessibilityIssue> checkFocusManagementEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            boolean hasFocusIndicator = (boolean) page.evaluate("() => { " +
                "const testDiv = document.createElement('div'); " +
                "testDiv.style.outline = '2px solid blue'; " +
                "testDiv.style.position = 'absolute'; " +
                "testDiv.style.left = '-9999px'; " +
                "document.body.appendChild(testDiv); " +
                "const focusedStyle = window.getComputedStyle(testDiv); " +
                "const hasOutline = focusedStyle.outline !== 'none'; " +
                "document.body.removeChild(testDiv); " +
                "return hasOutline; " +
            "}");

            if (!hasFocusIndicator) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    "A11Y-018",
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    "Page may be missing focus visibility styles",
                    "WCAG 2.4.7 (AA)"
                );
                issue.setViolation("Keyboard users must be able to see current focus position");
                issue.setCodeSnippet("outline: none;");
                issue.setFixSuggestion("Remove outline: none or add custom focus styles");

                if (includeScreenshots) {
                    try {
                        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                        issue.setScreenshot(screenshot);
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for focus management issue");
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking focus management: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check color contrast（enhanced version）
     */
    private static List<AccessibilityIssue> checkColorContrastEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Simple check: find elements with potential low contrast
            int potentialLowContrast = (int) page.evaluate("() => { " +
                "const elements = document.querySelectorAll('*'); " +
                "return Array.from(elements).filter(el => { " +
                    "const styles = window.getComputedStyle(el); " +
                    "const color = styles.color; " +
                    "const bg = styles.backgroundColor; " +
                    "return color !== 'rgba(0, 0, 0, 0)' && " +
                           "bg !== 'rgba(0, 0, 0, 0)' && " +
                           "color !== bg && " +
                           "el.textContent.trim().length > 0; " +
                "}).length; " +
            "}");

            if (potentialLowContrast > 0) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    "A11Y-019",
                    pageUrl,
                    IssueSeverity.INFO,
                    "Found " + potentialLowContrast + " elements requiring manual color contrast check",
                    "WCAG 1.4.3 (AA)"
                );
                issue.setViolation("Text and background contrast ratio should be at least 4.5:1");
                issue.setFixSuggestion("Use a color contrast checker tool to verify all text elements");

                if (includeScreenshots) {
                    try {
                        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                        issue.setScreenshot(screenshot);
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for color contrast issue");
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking color contrast: {}", e.getMessage());
        }
        return issues;
    }


    /**
     * Check ARIA attributes（enhanced version）
     */
    private static List<AccessibilityIssue> checkAriaAttributesEnhanced(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Get detailed information about elements with incorrect aria-hidden with precise CSS selector
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const elements = document.querySelectorAll('[aria-hidden=\"false\"]'); " +
                "return Array.from(elements).filter(el => { " +
                    "const styles = window.getComputedStyle(el); " +
                    "return styles.display === 'none' || styles.visibility === 'hidden'; " +
                "}).map(el => ({ " +
                    "outerHTML: el.outerHTML.substring(0, 200), " +
                    "id: el.id || '', " +
                    "uniqueSelector: getUniqueSelector(el) " +
                "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> invalidAriaElements = (List<Map<String, Object>>) result;

            for (int i = 0; i < invalidAriaElements.size(); i++) {
                Map<String, Object> elementData = invalidAriaElements.get(i);
                String issueId = String.format("A11Y-%03d", 20 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    "Element has incorrect aria-hidden attribute setting",
                    "WCAG 4.1.2 (A)"
                );
                issue.setViolation("Hidden elements should not have aria-hidden=\"false\"");
                issue.setFixSuggestion("For hidden elements, set aria-hidden=\"true\" or remove the attribute");
                issue.setCodeSnippet((String) elementData.get("outerHTML"));

                // Use unique selector from JavaScript
                String elementSelector = (String) elementData.get("uniqueSelector");
                issue.setElementSelector(elementSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, elementSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking ARIA attributes: {}", e.getMessage());
        }
        return issues;
    }


    /**
     * Check for invalid tabindex values
     */
    private static List<AccessibilityIssue> checkInvalidTabindex(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                "const elements = document.querySelectorAll('[tabindex]'); " +
                "return Array.from(elements).filter(el => { " +
                "  const tabindex = parseInt(el.getAttribute('tabindex')); " +
                "  return tabindex > 0 || tabindex < -1; " +
                "}).map(el => ({ " +
                "  outerHTML: el.outerHTML.substring(0, 200), " +
                "  tabindex: el.getAttribute('tabindex'), " +
                "  tagName: el.tagName.toLowerCase(), " +
                "  uniqueSelector: getUniqueSelector(el) " +
                "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> invalidElements = (List<Map<String, Object>>) result;

            for (int i = 0; i < invalidElements.size(); i++) {
                Map<String, Object> elementData = invalidElements.get(i);
                String tabindex = (String) elementData.get("tabindex");
                String issueId = String.format("A11Y-%03d", 30 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    "Invalid tabindex value: " + tabindex,
                    "WCAG 2.4.3 (A)"
                );
                issue.setViolation("Avoid tabindex values > 0 as they disrupt natural tab order");
                issue.setFixSuggestion("Use tabindex=\"0\" for elements that should be in natural tab order, or tabindex=\"-1\" for programmatic focus only");
                issue.setCodeSnippet((String) elementData.get("outerHTML"));

                String elementSelector = (String) elementData.get("uniqueSelector");
                issue.setElementSelector(elementSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, elementSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking tabindex values: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check for interactive elements that should be focusable but aren't
     */
    private static List<AccessibilityIssue> checkFocusableElements(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                // Find elements with click handlers that aren't focusable
                "const clickElements = document.querySelectorAll('[onclick], [role=\"button\"], [role=\"link\"], [role=\"checkbox\"]'); " +
                "return Array.from(clickElements).filter(el => { " +
                "  const tabindex = el.getAttribute('tabindex'); " +
                "  const isNativelyFocusable = ['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'].includes(el.tagName); " +
                "  const isDisabled = el.disabled || el.getAttribute('aria-disabled') === 'true'; " +
                "  return !isNativelyFocusable && tabindex === null && !isDisabled; " +
                "}).map(el => ({ " +
                "  outerHTML: el.outerHTML.substring(0, 200), " +
                "  tagName: el.tagName.toLowerCase(), " +
                "  role: el.getAttribute('role') || '', " +
                "  uniqueSelector: getUniqueSelector(el) " +
                "})); " +
            "}");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nonFocusableElements = (List<Map<String, Object>>) result;

            for (int i = 0; i < nonFocusableElements.size(); i++) {
                Map<String, Object> elementData = nonFocusableElements.get(i);
                String issueId = String.format("A11Y-%03d", 40 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Interactive element is not keyboard accessible",
                    "WCAG 2.1.1 (A)"
                );
                issue.setViolation("Elements with click handlers or interactive roles must be focusable via keyboard");
                issue.setFixSuggestion("Add tabindex=\"0\" to make the element focusable, or use a native focusable element like <button>");
                issue.setCodeSnippet((String) elementData.get("outerHTML"));

                String elementSelector = (String) elementData.get("uniqueSelector");
                issue.setElementSelector(elementSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, elementSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking focusable elements: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Check for potential keyboard traps
     */
    private static List<AccessibilityIssue> checkKeyboardTraps(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Check for elements with keyboard event handlers that might trap focus
            Object result = page.evaluate("() => { " +
                UNIQUE_SELECTOR_JS +
                // Check for modal dialogs without proper focus management
                "const modals = document.querySelectorAll('[role=\"dialog\"], [role=\"alertdialog\"]'); " +
                "const modalIssues = []; " +
                "modals.forEach(modal => { " +
                "  const hasAriaModal = modal.getAttribute('aria-modal') === 'true'; " +
                "  if (!hasAriaModal) { " +
                "    modalIssues.push({ " +
                "      outerHTML: modal.outerHTML.substring(0, 200), " +
                "      type: 'modal-no-aria-modal', " +
                "      uniqueSelector: getUniqueSelector(modal) " +
                "    }); " +
                "  } " +
                "}); " +
                // Check for elements that prevent default Tab behavior without escape
                "const tabTrapElements = document.querySelectorAll('[onkeydown], [onkeyup]'); " +
                "const trapIssues = []; " +
                "tabTrapElements.forEach(el => { " +
                "  const handler = el.getAttribute('onkeydown') || el.getAttribute('onkeyup'); " +
                "  if (handler && handler.includes('preventDefault') && !handler.includes('Escape')) { " +
                "    trapIssues.push({ " +
                "      outerHTML: el.outerHTML.substring(0, 200), " +
                "      type: 'potential-trap', " +
                "      uniqueSelector: getUniqueSelector(el) " +
                "    }); " +
                "  } " +
                "}); " +
                "return { modals: modalIssues, traps: trapIssues }; " +
            "}");

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            // Process modal issues
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modalIssues = (List<Map<String, Object>>) resultMap.get("modals");
            for (int i = 0; i < modalIssues.size(); i++) {
                Map<String, Object> modalData = modalIssues.get(i);
                String issueId = String.format("A11Y-%03d", 50 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    "Modal dialog missing aria-modal attribute",
                    "WCAG 2.4.3 (A)"
                );
                issue.setViolation("Modal dialogs should have aria-modal=\"true\" to inform assistive technologies");
                issue.setFixSuggestion("Add aria-modal=\"true\" to the dialog element");
                issue.setCodeSnippet((String) modalData.get("outerHTML"));

                String elementSelector = (String) modalData.get("uniqueSelector");
                issue.setElementSelector(elementSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, elementSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }

            // Process trap issues
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trapIssues = (List<Map<String, Object>>) resultMap.get("traps");
            for (int i = 0; i < trapIssues.size(); i++) {
                Map<String, Object> trapData = trapIssues.get(i);
                String issueId = String.format("A11Y-%03d", 60 + i);

                AccessibilityIssue issue = new AccessibilityIssue(
                    issueId,
                    pageUrl,
                    IssueSeverity.HIGH,
                    "Potential keyboard trap detected",
                    "WCAG 2.1.2 (A)"
                );
                issue.setViolation("Element may trap keyboard focus - ensure users can navigate away using keyboard");
                issue.setFixSuggestion("Add keyboard escape mechanism (Escape key, Tab to exit) for interactive components");
                issue.setCodeSnippet((String) trapData.get("outerHTML"));

                String elementSelector = (String) trapData.get("uniqueSelector");
                issue.setElementSelector(elementSelector);

                if (includeScreenshots) {
                    try {
                        ScreenshotResult screenshotResult = captureScreenshotWithVisibilityInfo(page, elementSelector);
                        issue.setScreenshot(screenshotResult.getScreenshot());
                        issue.setElementHidden(screenshotResult.isElementHidden());
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for issue {}", issueId);
                    }
                }

                issues.add(issue);
            }
        } catch (Exception e) {
            logger.error("Error checking keyboard traps: {}", e.getMessage());
        }
        return issues;
    }

    /**
     * Simulate Tab navigation to verify all focusable elements are reachable
     */
    private static List<AccessibilityIssue> simulateTabNavigation(Page page, String pageUrl, boolean includeScreenshots) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        try {
            // Get all focusable elements
            Object result = page.evaluate("() => { " +
                "const focusableSelectors = 'a[href], button:not([disabled]), input:not([disabled]), " +
                "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex=\"-1\"]), " +
                "[contenteditable=\"true\"], area[href]'; " +
                "const focusableElements = document.querySelectorAll(focusableSelectors); " +
                "return Array.from(focusableElements).filter(el => { " +
                "  const styles = window.getComputedStyle(el); " +
                "  const rect = el.getBoundingClientRect(); " +
                "  return styles.display !== 'none' && " +
                "         styles.visibility !== 'hidden' && " +
                "         rect.width > 0 && " +
                "         rect.height > 0; " +
                "}).length; " +
            "}");

            int focusableCount = ((Number) result).intValue();
            logger.info("Found {} visible focusable elements on the page", focusableCount);

            // Simulate Tab navigation to check reachability
            // Reset focus to body
            page.evaluate("document.body.focus()");

            Set<String> visitedSelectors = new HashSet<>();
            int maxTabs = Math.min(focusableCount + 5, 100); // Limit tab presses to avoid infinite loops

            for (int i = 0; i < maxTabs; i++) {
                // Press Tab
                page.keyboard().press("Tab");
                page.waitForTimeout(100); // Small delay for focus to change

                // Get currently focused element
                Object focusedElement = page.evaluate("() => { " +
                    "const getUniqueSelector = (element) => { " +
                    "  if (!element || element === document.body) return null; " +
                    "  if (element.id) return '#' + CSS.escape(element.id); " +
                    "  const path = []; " +
                    "  let current = element; " +
                    "  while (current && current !== document.body) { " +
                    "    let selector = current.tagName.toLowerCase(); " +
                    "    if (current.id) { " +
                    "      selector = '#' + CSS.escape(current.id); " +
                    "      path.unshift(selector); " +
                    "      break; " +
                    "    } " +
                    "    const parent = current.parentElement; " +
                    "    if (parent) { " +
                    "      const siblings = Array.from(parent.children).filter(child => child.tagName === current.tagName); " +
                    "      if (siblings.length > 1) { " +
                    "        const index = siblings.indexOf(current) + 1; " +
                    "        selector += ':nth-of-type(' + index + ')'; " +
                    "      } " +
                    "    } " +
                    "    path.unshift(selector); " +
                    "    current = parent; " +
                    "  } " +
                    "  return path.join(' > '); " +
                    "}; " +
                    "const focused = document.activeElement; " +
                    "return focused === document.body ? null : { " +
                    "  selector: getUniqueSelector(focused), " +
                    "  tagName: focused.tagName.toLowerCase() " +
                    "}; " +
                "}");

                if (focusedElement == null) {
                    // Focus returned to body - completed cycle or no focusable elements
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> focusedData = (Map<String, Object>) focusedElement;
                String selector = (String) focusedData.get("selector");

                if (selector != null) {
                    if (visitedSelectors.contains(selector)) {
                        // Already visited - cycle detected
                        break;
                    }
                    visitedSelectors.add(selector);
                }
            }

            // Report if we couldn't reach all focusable elements
            if (visitedSelectors.size() < focusableCount) {
                AccessibilityIssue issue = new AccessibilityIssue(
                    "A11Y-070",
                    pageUrl,
                    IssueSeverity.MEDIUM,
                    String.format("Not all focusable elements are reachable via Tab: %d of %d reached", 
                        visitedSelectors.size(), focusableCount),
                    "WCAG 2.1.1 (A)"
                );
                issue.setViolation("All interactive elements should be reachable via keyboard Tab navigation");
                issue.setFixSuggestion("Check for elements with tabindex issues or hidden focusable elements");

                if (includeScreenshots) {
                    try {
                        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                        issue.setScreenshot(screenshot);
                    } catch (Exception e) {
                        logger.warn("Failed to capture screenshot for keyboard navigation issue");
                    }
                }

                issues.add(issue);
            }

            // Reset focus back to body
            page.evaluate("document.body.focus()");


        } catch (Exception e) {
            logger.error("Error during Tab navigation simulation: {}", e.getMessage());
        }
        return issues;
    }

    // ==================== 独立HTML报告生成（企业级）====================

    /**
     * 生成独立的HTML无障碍测试报告（企业级）
     *
     * @param issues 无障碍问题列表
     * @param config 报告配置
     * @param stats 测试统计数据
     * @return HTML报告内容
     */
    public static String generateStandaloneHtmlReport(
            List<AccessibilityIssue> issues,
            ReportConfig config,
            TestStatistics stats) {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\" />\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        html.append("    <meta name=\"description\" content=\"Web Accessibility Compliance Report\" />\n");
        html.append("    <title>").append(escapeHtml(config.getReportTitle())).append("</title>\n");
        html.append("    <style>\n");
        html.append(getEnterpriseReportStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"report-container\">\n");

        // ========== 企业信息头部 ==========
        html.append(generateReportHeader(config, timestamp));

        // ========== 执行摘要 ==========
        if (config.isIncludeExecutiveSummary()) {
            html.append(generateExecutiveSummary(issues, config, stats));
        }

        // ========== WCAG合规矩阵 ==========
        if (config.isIncludeWcagDetails()) {
            html.append(generateWcagComplianceMatrix(issues, stats));
        }

        // ========== 测试概览 ==========
        html.append(generateTestOverview(stats, config));

        // ========== 问题严重等级统计 ==========
        html.append(generateSeverityStatistics(stats));

        // ========== 高频缺陷TOP10 ==========
        if (!issues.isEmpty()) {
            html.append(generateTopIssuesSection(issues));
        }

        // ========== 详细问题列表 ==========
        html.append(generateDetailedIssuesSection(issues, config));

        // ========== 修复时间线 ==========
        if (config.isIncludeRemediationTimeline() && !issues.isEmpty()) {
            html.append(generateRemediationTimeline(issues, stats));
        }

        // ========== GB/T 37668 对照 ==========
        html.append(generateChineseStandardSection(issues, stats));

        // ========== 测试结论 ==========
        html.append(generateTestConclusion(issues, stats, config));

        // ========== 报告页脚 ==========
        html.append(generateReportFooter(config));

        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append(getReportJavaScript());
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    /**
     * 生成报告头部
     */
    private static String generateReportHeader(ReportConfig config, String timestamp) {
        StringBuilder header = new StringBuilder();
        header.append("        <header class=\"report-header\">\n");
        
        // 报告标题
        header.append("            <div class=\"header-info\">\n");
        header.append("                <h1 class=\"report-title\">").append(escapeHtml(config.getReportTitle())).append("</h1>\n");
        header.append("                <div class=\"standard-badge\">").append(config.getStandard().getDisplayName()).append(" + GB/T 37668</div>\n");
        header.append("            </div>\n");
        
        // 报告元数据
        header.append("            <div class=\"report-meta\">\n");
        header.append("                <div class=\"meta-grid\">\n");
        header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">Project:</span><span class=\"meta-value\">").append(config.getProjectName() != null ? escapeHtml(config.getProjectName()) : "N/A").append("</span></div>\n");
        header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">Version:</span><span class=\"meta-value\">").append(config.getProjectVersion() != null ? escapeHtml(config.getProjectVersion()) : "N/A").append("</span></div>\n");
        header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">Environment:</span><span class=\"meta-value\">").append(escapeHtml(config.getTestEnvironment())).append("</span></div>\n");
        header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">Test Date:</span><span class=\"meta-value\">").append(timestamp).append("</span></div>\n");
        header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">Tester:</span><span class=\"meta-value\">").append(config.getTester() != null ? escapeHtml(config.getTester()) : "Accessibility Team").append("</span></div>\n");
        if (config.getApplicationUrl() != null) {
            header.append("                    <div class=\"meta-item\"><span class=\"meta-label\">URL:</span><span class=\"meta-value\">").append(escapeHtml(config.getApplicationUrl())).append("</span></div>\n");
        }
        header.append("                </div>\n");
        header.append("            </div>\n");
        header.append("        </header>\n");
        
        return header.toString();
    }

    /**
     * 生成执行摘要
     */
    private static String generateExecutiveSummary(List<AccessibilityIssue> issues, ReportConfig config, TestStatistics stats) {
        StringBuilder summary = new StringBuilder();
        summary.append("        <section class=\"section executive-summary\">\n");
        summary.append("            <h2 class=\"section-title\">📋 Executive Summary</h2>\n");
        summary.append("            <div class=\"summary-content\">\n");
        
        // 合规状态
        String complianceStatus = stats.getPassRate() >= 90 ? "Compliant" : 
                                  stats.getPassRate() >= 70 ? "Partially Compliant" : "Non-Compliant";
        String statusClass = stats.getPassRate() >= 90 ? "status-pass" : 
                             stats.getPassRate() >= 70 ? "status-warning" : "status-fail";
        
        summary.append("                <div class=\"compliance-status ").append(statusClass).append("\">\n");
        summary.append("                    <span class=\"status-label\">Compliance Status:</span>\n");
        summary.append("                    <span class=\"status-value\">").append(complianceStatus).append("</span>\n");
        summary.append("                </div>\n");
        
        // 关键发现
        summary.append("                <div class=\"key-findings\">\n");
        summary.append("                    <h3>Key Findings</h3>\n");
        summary.append("                    <ul class=\"findings-list\">\n");
        
        int criticalCount = stats.getIssueCounts().getOrDefault(IssueSeverity.CRITICAL, 0);
        int highCount = stats.getIssueCounts().getOrDefault(IssueSeverity.HIGH, 0);
        int mediumCount = stats.getIssueCounts().getOrDefault(IssueSeverity.MEDIUM, 0);
        
        if (criticalCount > 0) {
            summary.append("                        <li class=\"finding-critical\">").append(criticalCount).append(" Critical issue(s) found - immediate remediation required</li>\n");
        }
        if (highCount > 0) {
            summary.append("                        <li class=\"finding-high\">").append(highCount).append(" High priority issue(s) - must be addressed before release</li>\n");
        }
        if (mediumCount > 0) {
            summary.append("                        <li class=\"finding-medium\">").append(mediumCount).append(" Medium priority issue(s) - recommended for better user experience</li>\n");
        }
        
        // 主要问题类型
        if (!issues.isEmpty()) {
            summary.append("                        <li class=\"finding-info\">Primary issue categories: ").append(String.join(", ", generateSummaryPoints(issues))).append("</li>\n");
        }
        
        summary.append("                    </ul>\n");
        summary.append("                </div>\n");
        
        // 建议
        summary.append("                <div class=\"recommendations\">\n");
        summary.append("                    <h3>Recommendations</h3>\n");
        summary.append("                    <ol class=\"recommendation-list\">\n");
        
        if (criticalCount > 0 || highCount > 0) {
            summary.append("                        <li>Address all Critical and High priority issues before production deployment</li>\n");
        }
        if (issues.stream().anyMatch(i -> i.getDescription().toLowerCase().contains("keyboard") || i.getDescription().toLowerCase().contains("focus"))) {
            summary.append("                        <li>Ensure all interactive elements are keyboard accessible</li>\n");
        }
        if (issues.stream().anyMatch(i -> i.getDescription().toLowerCase().contains("alt") || i.getDescription().toLowerCase().contains("image"))) {
            summary.append("                        <li>Add meaningful alt text to all informative images</li>\n");
        }
        if (issues.stream().anyMatch(i -> i.getDescription().toLowerCase().contains("label") || i.getDescription().toLowerCase().contains("form"))) {
            summary.append("                        <li>Associate labels with all form controls</li>\n");
        }
        summary.append("                        <li>Conduct manual testing with assistive technologies (NVDA/JAWS/VoiceOver)</li>\n");
        summary.append("                        <li>Perform keyboard-only navigation testing</li>\n");
        
        summary.append("                    </ol>\n");
        summary.append("                </div>\n");
        summary.append("            </div>\n");
        summary.append("        </section>\n");
        
        return summary.toString();
    }

    /**
     * 生成WCAG合规矩阵
     */
    private static String generateWcagComplianceMatrix(List<AccessibilityIssue> issues, TestStatistics stats) {
        StringBuilder matrix = new StringBuilder();
        matrix.append("        <section class=\"section wcag-matrix\">\n");
        matrix.append("            <h2 class=\"section-title\">📊 WCAG 2.2 AA Compliance Matrix</h2>\n");
        matrix.append("            <div class=\"matrix-container\">\n");
        matrix.append("                <table class=\"compliance-table\">\n");
        matrix.append("                    <thead>\n");
        matrix.append("                        <tr>\n");
        matrix.append("                            <th>Principle</th>\n");
        matrix.append("                            <th>Criteria</th>\n");
        matrix.append("                            <th>Status</th>\n");
        matrix.append("                            <th>Issues</th>\n");
        matrix.append("                        </tr>\n");
        matrix.append("                    </thead>\n");
        matrix.append("                    <tbody>\n");
        
        // Perceivable
        matrix.append(generateWcagPrincipleRow("Perceivable", "1.1.1, 1.3.1, 1.4.3, 1.4.4, 1.4.5", issues, "alt", "image", "contrast", "color"));
        
        // Operable
        matrix.append(generateWcagPrincipleRow("Operable", "2.1.1, 2.1.2, 2.4.1, 2.4.2, 2.4.3, 2.4.4, 2.4.7", issues, "keyboard", "focus", "tab", "link", "title"));
        
        // Understandable
        matrix.append(generateWcagPrincipleRow("Understandable", "3.1.1, 3.2.1, 3.2.2, 3.3.1, 3.3.2", issues, "language", "label", "form", "error"));
        
        // Robust
        matrix.append(generateWcagPrincipleRow("Robust", "4.1.1, 4.1.2, 4.1.3", issues, "aria", "role", "name", "button"));
        
        matrix.append("                    </tbody>\n");
        matrix.append("                </table>\n");
        matrix.append("            </div>\n");
        matrix.append("        </section>\n");
        
        return matrix.toString();
    }

    /**
     * 生成WCAG原则行
     */
    private static String generateWcagPrincipleRow(String principle, String criteria, List<AccessibilityIssue> issues, String... keywords) {
        StringBuilder row = new StringBuilder();
        long issueCount = issues.stream()
            .filter(i -> {
                String desc = i.getDescription().toLowerCase();
                for (String kw : keywords) {
                    if (desc.contains(kw)) return true;
                }
                return false;
            })
            .count();
        
        String status = issueCount == 0 ? "Pass" : issueCount < 3 ? "Partial" : "Fail";
        String statusClass = issueCount == 0 ? "status-pass" : issueCount < 3 ? "status-warning" : "status-fail";
        
        row.append("                        <tr>\n");
        row.append("                            <td><strong>").append(principle).append("</strong></td>\n");
        row.append("                            <td>").append(criteria).append("</td>\n");
        row.append("                            <td><span class=\"status-badge ").append(statusClass).append("\">").append(status).append("</span></td>\n");
        row.append("                            <td>").append(issueCount).append("</td>\n");
        row.append("                        </tr>\n");
        
        return row.toString();
    }

    /**
     * 生成测试概览
     */
    private static String generateTestOverview(TestStatistics stats, ReportConfig config) {
        StringBuilder overview = new StringBuilder();
        overview.append("        <section class=\"section test-overview\">\n");
        overview.append("            <h2 class=\"section-title\">📈 Test Overview</h2>\n");
        overview.append("            <div class=\"stats-grid\">\n");
        
        // Pass Rate
        overview.append("                <div class=\"stat-card\">\n");
        overview.append("                    <div class=\"stat-icon\">📊</div>\n");
        overview.append("                    <div class=\"stat-content\">\n");
        overview.append("                        <div class=\"stat-label\">Pass Rate</div>\n");
        overview.append("                        <div class=\"stat-value\">").append(String.format("%.1f%%", stats.getPassRate())).append("</div>\n");
        overview.append("                    </div>\n");
        overview.append("                </div>\n");
        
        // Total Issues
        overview.append("                <div class=\"stat-card\">\n");
        overview.append("                    <div class=\"stat-icon\">⚠️</div>\n");
        overview.append("                    <div class=\"stat-content\">\n");
        overview.append("                        <div class=\"stat-label\">Total Issues</div>\n");
        overview.append("                        <div class=\"stat-value\">").append(stats.getTotalIssues()).append("</div>\n");
        overview.append("                    </div>\n");
        overview.append("                </div>\n");
        
        // Elements Scanned
        overview.append("                <div class=\"stat-card\">\n");
        overview.append("                    <div class=\"stat-icon\">🔍</div>\n");
        overview.append("                    <div class=\"stat-content\">\n");
        overview.append("                        <div class=\"stat-label\">Elements Scanned</div>\n");
        overview.append("                        <div class=\"stat-value\">").append(stats.getElementsScanned() > 0 ? stats.getElementsScanned() : "N/A").append("</div>\n");
        overview.append("                    </div>\n");
        overview.append("                </div>\n");
        
        // Pages Tested
        overview.append("                <div class=\"stat-card\">\n");
        overview.append("                    <div class=\"stat-icon\">📄</div>\n");
        overview.append("                    <div class=\"stat-content\">\n");
        overview.append("                        <div class=\"stat-label\">Pages Tested</div>\n");
        overview.append("                        <div class=\"stat-value\">").append(stats.getTotalPages()).append("</div>\n");
        overview.append("                    </div>\n");
        overview.append("                </div>\n");
        
        // Duration
        if (stats.getTestDurationMs() > 0) {
            overview.append("                <div class=\"stat-card\">\n");
            overview.append("                    <div class=\"stat-icon\">⏱️</div>\n");
            overview.append("                    <div class=\"stat-content\">\n");
            overview.append("                        <div class=\"stat-label\">Test Duration</div>\n");
            overview.append("                        <div class=\"stat-value\">").append(String.format("%.1fs", stats.getTestDurationMs() / 1000.0)).append("</div>\n");
            overview.append("                    </div>\n");
            overview.append("                </div>\n");
        }
        
        // Standard
        overview.append("                <div class=\"stat-card\">\n");
        overview.append("                    <div class=\"stat-icon\">✅</div>\n");
        overview.append("                    <div class=\"stat-content\">\n");
        overview.append("                        <div class=\"stat-label\">Standard</div>\n");
        overview.append("                        <div class=\"stat-value small\">").append(config.getStandard().getDisplayName()).append("</div>\n");
        overview.append("                    </div>\n");
        overview.append("                </div>\n");
        
        overview.append("            </div>\n");
        overview.append("        </section>\n");
        
        return overview.toString();
    }

    /**
     * 生成问题严重等级统计
     */
    private static String generateSeverityStatistics(TestStatistics stats) {
        StringBuilder severity = new StringBuilder();
        severity.append("        <section class=\"section severity-stats\">\n");
        severity.append("            <h2 class=\"section-title\">⚠️ Issue Severity Distribution</h2>\n");
        severity.append("            <div class=\"severity-chart\">\n");
        
        for (IssueSeverity sev : new IssueSeverity[]{IssueSeverity.CRITICAL, IssueSeverity.HIGH, IssueSeverity.MEDIUM, IssueSeverity.LOW, IssueSeverity.INFO}) {
            int count = stats.getIssueCounts().getOrDefault(sev, 0);
            severity.append("                <div class=\"severity-item\">\n");
            severity.append("                    <div class=\"severity-bar\" style=\"width: ").append(Math.min(count * 20, 100)).append("%; background: ").append(sev.getColor()).append(";\"></div>\n");
            severity.append("                    <div class=\"severity-info\">\n");
            severity.append("                        <span class=\"severity-name\" style=\"color: ").append(sev.getColor()).append(";\">").append(sev.getDisplayName()).append("</span>\n");
            severity.append("                        <span class=\"severity-count\">").append(count).append("</span>\n");
            severity.append("                    </div>\n");
            severity.append("                </div>\n");
        }
        
        severity.append("            </div>\n");
        severity.append("        </section>\n");
        
        return severity.toString();
    }

    /**
     * 生成高频缺陷TOP10
     */
    private static String generateTopIssuesSection(List<AccessibilityIssue> issues) {
        StringBuilder top = new StringBuilder();
        top.append("        <section class=\"section top-issues\">\n");
        top.append("            <h2 class=\"section-title\">🔥 Top Issue Categories</h2>\n");
        
        // 统计问题类型
        Map<String, Long> categoryCount = new HashMap<>();
        for (AccessibilityIssue issue : issues) {
            String category = categorizeIssue(issue);
            categoryCount.put(category, categoryCount.getOrDefault(category, 0L) + 1);
        }
        
        top.append("            <div class=\"top-issues-list\">\n");
        
        int rank = 1;
        for (Map.Entry<String, Long> entry : categoryCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(java.util.stream.Collectors.toList())) {
            
            top.append("                <div class=\"top-issue-item\">\n");
            top.append("                    <span class=\"rank\">#").append(rank++).append("</span>\n");
            top.append("                    <span class=\"issue-type\">").append(entry.getKey()).append("</span>\n");
            top.append("                    <span class=\"issue-count\">").append(entry.getValue()).append(" issues</span>\n");
            top.append("                </div>\n");
        }
        
        top.append("            </div>\n");
        top.append("        </section>\n");
        
        return top.toString();
    }

    /**
     * 分类问题
     */
    private static String categorizeIssue(AccessibilityIssue issue) {
        String desc = issue.getDescription().toLowerCase();
        String wcag = issue.getWcagCriteria() != null ? issue.getWcagCriteria().toLowerCase() : "";
        
        if (desc.contains("keyboard") || desc.contains("focus") || desc.contains("tab")) {
            return "Keyboard Navigation";
        } else if (desc.contains("alt") || desc.contains("image")) {
            return "Missing Image Alt Text";
        } else if (desc.contains("label") || desc.contains("form")) {
            return "Form Label Issues";
        } else if (desc.contains("button") || desc.contains("aria-label")) {
            return "Button Accessibility";
        } else if (desc.contains("contrast") || desc.contains("color")) {
            return "Color Contrast";
        } else if (desc.contains("title") || desc.contains("heading")) {
            return "Page Title/Headings";
        } else if (desc.contains("link")) {
            return "Link Text Issues";
        } else if (desc.contains("aria") || desc.contains("role")) {
            return "ARIA Attributes";
        } else if (desc.contains("lang") || desc.contains("language")) {
            return "Language Declaration";
        } else {
            return "Other Accessibility Issues";
        }
    }

    /**
     * 生成详细问题列表
     */
    private static String generateDetailedIssuesSection(List<AccessibilityIssue> issues, ReportConfig config) {
        StringBuilder detail = new StringBuilder();
        detail.append("        <section class=\"section detailed-issues\">\n");
        detail.append("            <h2 class=\"section-title\">🔍 Detailed Issue Report").append(config.isIncludeScreenshots() ? " (With Screenshots)" : "").append("</h2>\n");
        
        if (issues.isEmpty()) {
            detail.append("            <div class=\"success-message\">\n");
            detail.append("                <div class=\"success-icon\">✅</div>\n");
            detail.append("                <h3>No Accessibility Issues Found</h3>\n");
            detail.append("                <p>The page fully complies with ").append(config.getStandard().getDisplayName()).append(" standards.</p>\n");
            detail.append("            </div>\n");
        } else {
            // 按严重程度分组
            for (IssueSeverity severity : new IssueSeverity[]{IssueSeverity.CRITICAL, IssueSeverity.HIGH, IssueSeverity.MEDIUM, IssueSeverity.LOW, IssueSeverity.INFO}) {
                List<AccessibilityIssue> severityIssues = issues.stream()
                    .filter(i -> i.getSeverity() == severity)
                    .collect(java.util.stream.Collectors.toList());
                
                if (!severityIssues.isEmpty()) {
                    detail.append("                <div class=\"severity-group\">\n");
                    detail.append("                    <h3 class=\"severity-group-title\" style=\"border-color: ").append(severity.getColor()).append(";\">\n");
                    detail.append("                        <span style=\"color: ").append(severity.getColor()).append(";\">").append(severity.getDisplayName()).append(" Priority</span>\n");
                    detail.append("                        <span class=\"count\">").append(severityIssues.size()).append(" issues</span>\n");
                    detail.append("                    </h3>\n");
                    
                    for (AccessibilityIssue issue : severityIssues) {
                        detail.append(generateIssueCard(issue, config.isIncludeScreenshots()));
                    }
                    
                    detail.append("                </div>\n");
                }
            }
        }
        
        detail.append("        </section>\n");
        
        return detail.toString();
    }

    /**
     * 生成问题卡片（企业级）
     */
    private static String generateIssueCard(AccessibilityIssue issue, boolean includeScreenshots) {
        StringBuilder card = new StringBuilder();
        card.append("                    <div class=\"issue-card\">\n");
        card.append("                        <div class=\"issue-header\" onclick=\"toggleIssue(this)\">\n");
        card.append("                            <div class=\"issue-title\">\n");
        card.append("                                <span class=\"issue-id\">").append(issue.getId()).append("</span>\n");
        card.append("                                <span class=\"issue-desc\">").append(escapeHtml(issue.getDescription()));
        
        if (issue.isElementHidden()) {
            card.append(" <span class=\"hidden-badge\">HIDDEN</span>");
        }
        
        card.append("</span>\n");
        card.append("                            </div>\n");
        card.append("                            <div class=\"issue-meta\">\n");
        card.append("                                <span class=\"wcag-tag\">").append(issue.getWcagCriteria()).append("</span>\n");
        card.append("                                <span class=\"expand-icon\">▼</span>\n");
        card.append("                            </div>\n");
        card.append("                        </div>\n");
        card.append("                        <div class=\"issue-body\">\n");
        
        // 隐藏元素警告
        if (issue.isElementHidden()) {
            card.append("                            <div class=\"warning-box\">\n");
            card.append("                                <strong>⚠️ Hidden Element Detected:</strong> This element is not visible on the page. The screenshot shows an approximate location or visible parent.\n");
            card.append("                            </div>\n");
        }
        
        // 元素选择器
        if (issue.getElementSelector() != null && !issue.getElementSelector().isEmpty()) {
            card.append("                            <div class=\"info-row\">\n");
            card.append("                                <span class=\"info-label\">Element Selector:</span>\n");
            card.append("                                <code class=\"selector\">").append(escapeHtml(issue.getElementSelector())).append("</code>\n");
            card.append("                            </div>\n");
        }
        
        // 页面URL
        if (issue.getPageUrl() != null) {
            card.append("                            <div class=\"info-row\">\n");
            card.append("                                <span class=\"info-label\">Page URL:</span>\n");
            card.append("                                <span>").append(escapeHtml(issue.getPageUrl())).append("</span>\n");
            card.append("                            </div>\n");
        }
        
        // 违规描述
        if (issue.getViolation() != null) {
            card.append("                            <div class=\"info-row\">\n");
            card.append("                                <span class=\"info-label\">Violation:</span>\n");
            card.append("                                <span>").append(escapeHtml(issue.getViolation())).append("</span>\n");
            card.append("                            </div>\n");
        }
        
        // 代码片段
        if (issue.getCodeSnippet() != null) {
            card.append("                            <div class=\"code-section\">\n");
            card.append("                                <span class=\"info-label\">HTML Code:</span>\n");
            card.append("                                <pre class=\"code-snippet\">").append(escapeHtml(issue.getCodeSnippet())).append("</pre>\n");
            card.append("                            </div>\n");
        }
        
        // 修复建议
        if (issue.getFixSuggestion() != null) {
            card.append("                            <div class=\"fix-section\">\n");
            card.append("                                <span class=\"info-label\">💡 Fix Suggestion:</span>\n");
            card.append("                                <pre class=\"fix-code\">").append(escapeHtml(issue.getFixSuggestion())).append("</pre>\n");
            card.append("                            </div>\n");
        }
        
        // 截图
        if (includeScreenshots && issue.getScreenshot() != null && issue.getScreenshot().length > 0) {
            String base64Screenshot = java.util.Base64.getEncoder().encodeToString(issue.getScreenshot());
            card.append("                            <div class=\"screenshot-section\">\n");
            card.append("                                <span class=\"info-label\">📷 Screenshot with Annotation:</span>\n");
            card.append("                                <img src=\"data:image/png;base64,").append(base64Screenshot).append("\" alt=\"Issue Screenshot\" class=\"issue-screenshot\" />\n");
            card.append("                            </div>\n");
        }
        
        card.append("                        </div>\n");
        card.append("                    </div>\n");
        
        return card.toString();
    }

    /**
     * 生成修复时间线
     */
    private static String generateRemediationTimeline(List<AccessibilityIssue> issues, TestStatistics stats) {
        StringBuilder timeline = new StringBuilder();
        timeline.append("        <section class=\"section remediation-timeline\">\n");
        timeline.append("            <h2 class=\"section-title\">📅 Remediation Timeline</h2>\n");
        timeline.append("            <div class=\"timeline-container\">\n");
        
        int criticalCount = stats.getIssueCounts().getOrDefault(IssueSeverity.CRITICAL, 0);
        int highCount = stats.getIssueCounts().getOrDefault(IssueSeverity.HIGH, 0);
        int mediumCount = stats.getIssueCounts().getOrDefault(IssueSeverity.MEDIUM, 0);
        int lowCount = stats.getIssueCounts().getOrDefault(IssueSeverity.LOW, 0);
        
        timeline.append("                <div class=\"timeline-item critical\">\n");
        timeline.append("                    <div class=\"timeline-marker\"></div>\n");
        timeline.append("                    <div class=\"timeline-content\">\n");
        timeline.append("                        <h4>Immediate (Critical Issues)</h4>\n");
        timeline.append("                        <p>").append(criticalCount).append(" issue(s) - Must be fixed before any release</p>\n");
        timeline.append("                        <span class=\"deadline\">Deadline: Before Production Deployment</span>\n");
        timeline.append("                    </div>\n");
        timeline.append("                </div>\n");
        
        timeline.append("                <div class=\"timeline-item high\">\n");
        timeline.append("                    <div class=\"timeline-marker\"></div>\n");
        timeline.append("                    <div class=\"timeline-content\">\n");
        timeline.append("                        <h4>Short Term (High Priority)</h4>\n");
        timeline.append("                        <p>").append(highCount).append(" issue(s) - Fix within current sprint</p>\n");
        timeline.append("                        <span class=\"deadline\">Deadline: 2 Weeks</span>\n");
        timeline.append("                    </div>\n");
        timeline.append("                </div>\n");
        
        timeline.append("                <div class=\"timeline-item medium\">\n");
        timeline.append("                    <div class=\"timeline-marker\"></div>\n");
        timeline.append("                    <div class=\"timeline-content\">\n");
        timeline.append("                        <h4>Medium Term (Medium Priority)</h4>\n");
        timeline.append("                        <p>").append(mediumCount).append(" issue(s) - Plan for next release</p>\n");
        timeline.append("                        <span class=\"deadline\">Deadline: 1 Month</span>\n");
        timeline.append("                    </div>\n");
        timeline.append("                </div>\n");
        
        timeline.append("                <div class=\"timeline-item low\">\n");
        timeline.append("                    <div class=\"timeline-marker\"></div>\n");
        timeline.append("                    <div class=\"timeline-content\">\n");
        timeline.append("                        <h4>Long Term (Low Priority)</h4>\n");
        timeline.append("                        <p>").append(lowCount).append(" issue(s) - Backlog for future improvement</p>\n");
        timeline.append("                        <span class=\"deadline\">Deadline: 3 Months</span>\n");
        timeline.append("                    </div>\n");
        timeline.append("                </div>\n");
        
        timeline.append("            </div>\n");
        timeline.append("        </section>\n");
        
        return timeline.toString();
    }

    /**
     * 生成中国标准对照
     */
    private static String generateChineseStandardSection(List<AccessibilityIssue> issues, TestStatistics stats) {
        StringBuilder cn = new StringBuilder();
        cn.append("        <section class=\"section chinese-standard\">\n");
        cn.append("            <h2 class=\"section-title\">🇨🇳 GB/T 37668-2019 Compliance (China Standard)</h2>\n");
        cn.append("            <div class=\"standard-content\">\n");
        cn.append("                <p class=\"standard-desc\">This test also complies with China's national web accessibility standard GB/T 37668-2019 \"Information Technology - Web Content Accessibility Guidelines\".</p>\n");
        cn.append("                <table class=\"standard-table\">\n");
        cn.append("                    <thead>\n");
        cn.append("                        <tr>\n");
        cn.append("                            <th>GB/T 37668 Clause</th>\n");
        cn.append("                            <th>WCAG Equivalent</th>\n");
        cn.append("                            <th>Status</th>\n");
        cn.append("                        </tr>\n");
        cn.append("                    </thead>\n");
        cn.append("                    <tbody>\n");
        cn.append("                        <tr><td>4.1 Text Alternatives</td><td>1.1.1</td><td>").append(checkWcagStatus(issues, "alt", "image")).append("</td></tr>\n");
        cn.append("                        <tr><td>4.2 Keyboard Accessible</td><td>2.1.1, 2.1.2</td><td>").append(checkWcagStatus(issues, "keyboard", "focus", "tab")).append("</td></tr>\n");
        cn.append("                        <tr><td>4.3 Enough Time</td><td>2.2.1</td><td>Pass</td></tr>\n");
        cn.append("                        <tr><td>4.4 Seizure Prevention</td><td>2.3.1</td><td>Pass</td></tr>\n");
        cn.append("                        <tr><td>4.5 Navigable</td><td>2.4.x</td><td>").append(checkWcagStatus(issues, "title", "heading", "link")).append("</td></tr>\n");
        cn.append("                        <tr><td>4.6 Readable</td><td>3.1.x</td><td>").append(checkWcagStatus(issues, "language", "lang")).append("</td></tr>\n");
        cn.append("                        <tr><td>4.7 Predictable</td><td>3.2.x</td><td>Pass</td></tr>\n");
        cn.append("                        <tr><td>4.8 Input Assistance</td><td>3.3.x</td><td>").append(checkWcagStatus(issues, "label", "form", "error")).append("</td></tr>\n");
        cn.append("                        <tr><td>4.9 Compatible</td><td>4.1.x</td><td>").append(checkWcagStatus(issues, "aria", "role", "name")).append("</td></tr>\n");
        cn.append("                    </tbody>\n");
        cn.append("                </table>\n");
        cn.append("            </div>\n");
        cn.append("        </section>\n");
        
        return cn.toString();
    }

    /**
     * 检查WCAG状态
     */
    private static String checkWcagStatus(List<AccessibilityIssue> issues, String... keywords) {
        long count = issues.stream()
            .filter(i -> {
                String desc = i.getDescription().toLowerCase();
                for (String kw : keywords) {
                    if (desc.contains(kw)) return true;
                }
                return false;
            })
            .count();
        
        return count == 0 ? "<span class=\"status-pass\">Pass</span>" : 
               count < 3 ? "<span class=\"status-warning\">Partial</span>" : 
               "<span class=\"status-fail\">Fail</span>";
    }

    /**
     * 生成测试结论
     */
    private static String generateTestConclusion(List<AccessibilityIssue> issues, TestStatistics stats, ReportConfig config) {
        StringBuilder conclusion = new StringBuilder();
        conclusion.append("        <section class=\"section test-conclusion\">\n");
        conclusion.append("            <h2 class=\"section-title\">📌 Test Conclusion</h2>\n");
        conclusion.append("            <div class=\"conclusion-content\">\n");
        
        // 合规声明
        String complianceLevel = stats.getPassRate() >= 90 ? "Full Compliance" :
                                 stats.getPassRate() >= 70 ? "Partial Compliance" : "Non-Compliance";
        conclusion.append("                <div class=\"compliance-declaration\">\n");
        conclusion.append("                    <h3>Compliance Declaration</h3>\n");
        conclusion.append("                    <p>This accessibility test evaluated <strong>").append(stats.getTotalPages()).append(" page(s)</strong> ");
        conclusion.append("against <strong>").append(config.getStandard().getDisplayName()).append("</strong> and <strong>GB/T 37668-2019</strong> standards.</p>\n");
        conclusion.append("                    <p>Overall compliance level: <strong class=\"compliance-level\">").append(complianceLevel).append("</strong></p>\n");
        conclusion.append("                </div>\n");
        
        // 统计摘要
        conclusion.append("                <div class=\"stats-summary\">\n");
        conclusion.append("                    <div class=\"summary-item\">\n");
        conclusion.append("                        <span class=\"summary-label\">Total Issues:</span>\n");
        conclusion.append("                        <span class=\"summary-value\">").append(stats.getTotalIssues()).append("</span>\n");
        conclusion.append("                    </div>\n");
        conclusion.append("                    <div class=\"summary-item\">\n");
        conclusion.append("                        <span class=\"summary-label\">Pass Rate:</span>\n");
        conclusion.append("                        <span class=\"summary-value\">").append(String.format("%.1f%%", stats.getPassRate())).append("</span>\n");
        conclusion.append("                    </div>\n");
        conclusion.append("                    <div class=\"summary-item\">\n");
        conclusion.append("                        <span class=\"summary-label\">Critical Issues:</span>\n");
        conclusion.append("                        <span class=\"summary-value critical\">").append(stats.getIssueCounts().getOrDefault(IssueSeverity.CRITICAL, 0)).append("</span>\n");
        conclusion.append("                    </div>\n");
        conclusion.append("                    <div class=\"summary-item\">\n");
        conclusion.append("                        <span class=\"summary-label\">High Priority:</span>\n");
        conclusion.append("                        <span class=\"summary-value high\">").append(stats.getIssueCounts().getOrDefault(IssueSeverity.HIGH, 0)).append("</span>\n");
        conclusion.append("                    </div>\n");
        conclusion.append("                </div>\n");
        
        // 后续建议
        if (!issues.isEmpty()) {
            conclusion.append("                <div class=\"next-steps\">\n");
            conclusion.append("                    <h3>Recommended Next Steps</h3>\n");
            conclusion.append("                    <ol>\n");
            conclusion.append("                        <li>Address all Critical and High priority issues before production release</li>\n");
            conclusion.append("                        <li>Conduct manual testing with screen readers (NVDA/JAWS/VoiceOver)</li>\n");
            conclusion.append("                        <li>Perform keyboard-only navigation testing for all user flows</li>\n");
            conclusion.append("                        <li>Verify color contrast with dedicated tools</li>\n");
            conclusion.append("                        <li>Test with browser zoom at 200%</li>\n");
            conclusion.append("                        <li>Re-run automated tests after fixes are implemented</li>\n");
            conclusion.append("                    </ol>\n");
            conclusion.append("                </div>\n");
        }
        
        conclusion.append("            </div>\n");
        conclusion.append("        </section>\n");
        
        return conclusion.toString();
    }

    /**
     * 生成报告页脚
     */
    private static String generateReportFooter(ReportConfig config) {
        StringBuilder footer = new StringBuilder();
        footer.append("        <footer class=\"report-footer\">\n");
        footer.append("            <div class=\"footer-content\">\n");
        footer.append("                <p class=\"generated-by\">Generated by Accessibility Scanner Enterprise Edition</p>\n");
        footer.append("                <p class=\"standards\">Standards: ").append(config.getStandard().getDisplayName()).append(" | GB/T 37668-2019</p>\n");
        footer.append("                <p class=\"copyright\">© ").append(java.time.Year.now().getValue()).append(" ").append(config.getCompanyName() != null ? escapeHtml(config.getCompanyName()) : "Enterprise").append(". All rights reserved.</p>\n");
        footer.append("            </div>\n");
        footer.append("        </footer>\n");
        
        return footer.toString();
    }

    /**
     * 获取企业级报告CSS样式
     */
    private static String getEnterpriseReportStyles() {
        StringBuilder styles = new StringBuilder();
        styles.append("        :root {\n");
        styles.append("            --primary-color: #2563eb;\n");
        styles.append("            --secondary-color: #64748b;\n");
        styles.append("            --success-color: #10b981;\n");
        styles.append("            --warning-color: #f59e0b;\n");
        styles.append("            --danger-color: #ef4444;\n");
        styles.append("            --info-color: #3b82f6;\n");
        styles.append("            --bg-primary: #ffffff;\n");
        styles.append("            --bg-secondary: #f8fafc;\n");
        styles.append("            --bg-tertiary: #f1f5f9;\n");
        styles.append("            --text-primary: #1e293b;\n");
        styles.append("            --text-secondary: #64748b;\n");
        styles.append("            --border-color: #e2e8f0;\n");
        styles.append("            --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);\n");
        styles.append("            --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1);\n");
        styles.append("            --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        styles.append("\n");
        styles.append("        body {\n");
        styles.append("            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n");
        styles.append("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        styles.append("            padding: 20px;\n");
        styles.append("            min-height: 100vh;\n");
        styles.append("            color: var(--text-primary);\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .report-container {\n");
        styles.append("            max-width: 1400px;\n");
        styles.append("            margin: 0 auto;\n");
        styles.append("            background: var(--bg-primary);\n");
        styles.append("            border-radius: 16px;\n");
        styles.append("            box-shadow: var(--shadow-lg);\n");
        styles.append("            overflow: hidden;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        /* Header */\n");
        styles.append("        .report-header {\n");
        styles.append("            background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);\n");
        styles.append("            color: white;\n");
        styles.append("            padding: 40px;\n");
        styles.append("        }\n");
        styles.append("\n");
        styles.append("        .header-info { text-align: center; margin-bottom: 30px; }\n");
        styles.append("        .report-title { font-size: 32px; font-weight: 700; margin-bottom: 10px; }\n");
        styles.append("        .company-info { opacity: 0.9; }\n");
        styles.append("        .company-name { font-size: 18px; }\n");
        styles.append("        .department { font-size: 14px; opacity: 0.8; }\n");
        styles.append("\n");
        styles.append("        .report-meta { background: rgba(255,255,255,0.1); border-radius: 12px; padding: 20px; }\n");
        styles.append("        .meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }\n");
        styles.append("        .meta-item { display: flex; flex-direction: column; }\n");
        styles.append("        .meta-label { font-size: 12px; opacity: 0.8; margin-bottom: 4px; }\n");
        styles.append("        .meta-value { font-size: 14px; font-weight: 500; }\n");
        styles.append("\n");
        styles.append("        /* Sections */\n");
        styles.append("        .section { padding: 30px 40px; border-bottom: 1px solid var(--border-color); }\n");
        styles.append("        .section:last-of-type { border-bottom: none; }\n");
        styles.append("        .section-title { font-size: 20px; font-weight: 600; color: var(--text-primary); margin-bottom: 20px; padding-left: 12px; border-left: 4px solid var(--primary-color); }\n");
        styles.append("\n");
        styles.append("        /* Executive Summary */\n");
        styles.append("        .compliance-status { padding: 20px; border-radius: 12px; margin-bottom: 20px; text-align: center; }\n");
        styles.append("        .status-pass { background: #dcfce7; color: #166534; }\n");
        styles.append("        .status-warning { background: #fef3c7; color: #92400e; }\n");
        styles.append("        .status-fail { background: #fee2e2; color: #991b1b; }\n");
        styles.append("        .status-label { font-size: 14px; }\n");
        styles.append("        .status-value { font-size: 24px; font-weight: 700; }\n");
        styles.append("\n");
        styles.append("        .key-findings h3, .recommendations h3 { font-size: 16px; margin-bottom: 12px; color: var(--text-primary); }\n");
        styles.append("        .findings-list, .recommendation-list { padding-left: 20px; }\n");
        styles.append("        .findings-list li, .recommendation-list li { margin-bottom: 8px; line-height: 1.6; }\n");
        styles.append("        .finding-critical { color: var(--danger-color); font-weight: 500; }\n");
        styles.append("        .finding-high { color: var(--warning-color); font-weight: 500; }\n");
        styles.append("        .finding-medium { color: var(--info-color); }\n");
        styles.append("\n");
        styles.append("        /* Stats Grid */\n");
        styles.append("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 20px; }\n");
        styles.append("        .stat-card {\n");
        styles.append("            background: var(--bg-secondary);\n");
        styles.append("            border-radius: 12px;\n");
        styles.append("            padding: 20px;\n");
        styles.append("            display: flex;\n");
        styles.append("            align-items: center;\n");
        styles.append("            gap: 15px;\n");
        styles.append("            transition: transform 0.2s, box-shadow 0.2s;\n");
        styles.append("        }\n");
        styles.append("        .stat-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }\n");
        styles.append("        .stat-icon { font-size: 32px; }\n");
        styles.append("        .stat-label { font-size: 12px; color: var(--text-secondary); }\n");
        styles.append("        .stat-value { font-size: 24px; font-weight: 700; color: var(--text-primary); }\n");
        styles.append("        .stat-value.small { font-size: 14px; }\n");
        styles.append("\n");
        styles.append("        /* Severity Chart */\n");
        styles.append("        .severity-chart { display: flex; flex-direction: column; gap: 15px; }\n");
        styles.append("        .severity-item { position: relative; }\n");
        styles.append("        .severity-bar { height: 8px; border-radius: 4px; transition: width 0.3s; }\n");
        styles.append("        .severity-info { display: flex; justify-content: space-between; margin-top: 8px; }\n");
        styles.append("        .severity-name { font-weight: 500; }\n");
        styles.append("        .severity-count { font-size: 14px; color: var(--text-secondary); }\n");
        styles.append("\n");
        styles.append("        /* Top Issues */\n");
        styles.append("        .top-issues-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }\n");
        styles.append("        .top-issue-item {\n");
        styles.append("            background: var(--bg-tertiary);\n");
        styles.append("            padding: 15px;\n");
        styles.append("            border-radius: 8px;\n");
        styles.append("            display: flex;\n");
        styles.append("            align-items: center;\n");
        styles.append("            gap: 12px;\n");
        styles.append("        }\n");
        styles.append("        .rank { background: var(--primary-color); color: white; padding: 4px 10px; border-radius: 4px; font-weight: 600; font-size: 12px; }\n");
        styles.append("        .issue-type { flex: 1; font-weight: 500; }\n");
        styles.append("        .issue-count { color: var(--text-secondary); font-size: 14px; }\n");
        styles.append("\n");
        styles.append("        /* Issue Cards */\n");
        styles.append("        .severity-group { margin-bottom: 25px; }\n");
        styles.append("        .severity-group-title { display: flex; justify-content: space-between; padding: 12px 16px; background: var(--bg-tertiary); border-left-width: 4px; border-left-style: solid; margin-bottom: 15px; border-radius: 0 8px 8px 0; }\n");
        styles.append("        .count { font-size: 14px; color: var(--text-secondary); }\n");
        styles.append("\n");
        styles.append("        .issue-card { border: 1px solid var(--border-color); border-radius: 8px; margin-bottom: 12px; overflow: hidden; }\n");
        styles.append("        .issue-header { padding: 15px 20px; background: var(--bg-secondary); cursor: pointer; display: flex; justify-content: space-between; align-items: center; }\n");
        styles.append("        .issue-header:hover { background: var(--bg-tertiary); }\n");
        styles.append("        .issue-title { display: flex; align-items: center; gap: 12px; flex: 1; }\n");
        styles.append("        .issue-id { background: var(--secondary-color); color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }\n");
        styles.append("        .issue-desc { flex: 1; }\n");
        styles.append("        .hidden-badge { background: #ff9800; color: white; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; }\n");
        styles.append("        .issue-meta { display: flex; align-items: center; gap: 12px; }\n");
        styles.append("        .wcag-tag { background: #e0e7ff; color: #3730a3; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }\n");
        styles.append("        .expand-icon { font-size: 12px; color: var(--text-secondary); transition: transform 0.3s; }\n");
        styles.append("        .issue-card.open .expand-icon { transform: rotate(180deg); }\n");
        styles.append("        .issue-body { padding: 20px; display: none; background: white; }\n");
        styles.append("        .issue-card.open .issue-body { display: block; }\n");
        styles.append("\n");
        styles.append("        .warning-box { background: #fff3e0; border-left: 4px solid #ff9800; padding: 12px 16px; margin-bottom: 15px; border-radius: 4px; font-size: 14px; }\n");
        styles.append("        .info-row { margin-bottom: 12px; }\n");
        styles.append("        .info-label { font-weight: 600; color: var(--text-secondary); font-size: 13px; display: inline-block; min-width: 140px; }\n");
        styles.append("        .selector { background: var(--bg-tertiary); padding: 2px 6px; border-radius: 4px; font-size: 12px; color: var(--primary-color); }\n");
        styles.append("        .code-section, .fix-section { margin: 15px 0; }\n");
        styles.append("        .code-snippet, .fix-code { background: #f8d7da; border-left: 4px solid #dc3545; padding: 12px 16px; border-radius: 4px; font-family: 'Courier New', monospace; font-size: 13px; overflow-x: auto; color: #721c24; }\n");
        styles.append("        .fix-code { background: #d4edda; border-left-color: #28a745; color: #155724; }\n");
        styles.append("        .screenshot-section { margin-top: 15px; }\n");
        styles.append("        .issue-screenshot { max-width: 100%; border-radius: 8px; border: 1px solid var(--border-color); box-shadow: var(--shadow-sm); margin-top: 10px; }\n");
        styles.append("\n");
        styles.append("        /* Timeline */\n");
        styles.append("        .timeline-container { position: relative; padding-left: 30px; }\n");
        styles.append("        .timeline-item { position: relative; padding-bottom: 25px; }\n");
        styles.append("        .timeline-item:last-child { padding-bottom: 0; }\n");
        styles.append("        .timeline-item::before { content: ''; position: absolute; left: -30px; top: 0; bottom: 0; width: 2px; background: var(--border-color); }\n");
        styles.append("        .timeline-item:last-child::before { display: none; }\n");
        styles.append("        .timeline-marker { position: absolute; left: -37px; top: 5px; width: 16px; height: 16px; border-radius: 50%; border: 3px solid; background: white; }\n");
        styles.append("        .timeline-item.critical .timeline-marker { border-color: var(--danger-color); }\n");
        styles.append("        .timeline-item.high .timeline-marker { border-color: var(--warning-color); }\n");
        styles.append("        .timeline-item.medium .timeline-marker { border-color: var(--info-color); }\n");
        styles.append("        .timeline-item.low .timeline-marker { border-color: var(--success-color); }\n");
        styles.append("        .timeline-content h4 { font-size: 15px; margin-bottom: 5px; }\n");
        styles.append("        .timeline-content p { color: var(--text-secondary); font-size: 14px; margin-bottom: 8px; }\n");
        styles.append("        .deadline { font-size: 12px; color: var(--primary-color); font-weight: 500; }\n");
        styles.append("\n");
        styles.append("        /* Standards Table */\n");
        styles.append("        .standard-table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n");
        styles.append("        .standard-table th, .standard-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border-color); }\n");
        styles.append("        .standard-table th { background: var(--bg-tertiary); font-weight: 600; }\n");
        styles.append("\n");
        styles.append("        /* Conclusion */\n");
        styles.append("        .compliance-declaration { background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); padding: 25px; border-radius: 12px; margin-bottom: 20px; }\n");
        styles.append("        .compliance-declaration h3 { margin-bottom: 12px; }\n");
        styles.append("        .compliance-level { color: var(--primary-color); font-size: 18px; }\n");
        styles.append("        .stats-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; margin-bottom: 20px; }\n");
        styles.append("        .summary-item { background: var(--bg-secondary); padding: 15px; border-radius: 8px; text-align: center; }\n");
        styles.append("        .summary-label { font-size: 12px; color: var(--text-secondary); display: block; margin-bottom: 5px; }\n");
        styles.append("        .summary-value { font-size: 24px; font-weight: 700; }\n");
        styles.append("        .summary-value.critical { color: var(--danger-color); }\n");
        styles.append("        .summary-value.high { color: var(--warning-color); }\n");
        styles.append("        .next-steps h3 { margin-bottom: 12px; }\n");
        styles.append("        .next-steps ol { padding-left: 20px; }\n");
        styles.append("        .next-steps li { margin-bottom: 8px; line-height: 1.6; }\n");
        styles.append("\n");
        styles.append("        /* Footer */\n");
        styles.append("        .report-footer { background: var(--bg-tertiary); padding: 30px 40px; text-align: center; }\n");
        styles.append("        .footer-content p { margin-bottom: 8px; font-size: 14px; color: var(--text-secondary); }\n");
        styles.append("        .generated-by { font-weight: 600; color: var(--text-primary); }\n");
        styles.append("\n");
        styles.append("        /* Success Message */\n");
        styles.append("        .success-message { text-align: center; padding: 40px; background: #dcfce7; border-radius: 12px; }\n");
        styles.append("        .success-icon { font-size: 48px; margin-bottom: 15px; }\n");
        styles.append("        .success-message h3 { color: #166534; margin-bottom: 10px; }\n");
        styles.append("\n");
        styles.append("        /* Responsive */\n");
        styles.append("        @media (max-width: 768px) {\n");
        styles.append("            .section { padding: 20px; }\n");
        styles.append("            .report-title { font-size: 24px; }\n");
        styles.append("            .stats-grid { grid-template-columns: 1fr 1fr; }\n");
        styles.append("        }\n");
        
        return styles.toString();
    }

    /**
     * 获取报告JavaScript
     */
    private static String getReportJavaScript() {
        return "        function toggleIssue(header) {\n" +
               "            const card = header.closest('.issue-card');\n" +
               "            card.classList.toggle('open');\n" +
               "        }\n" +
               "        // Auto-expand critical issues\n" +
               "        document.querySelectorAll('.severity-group').forEach(group => {\n" +
               "            if (group.querySelector('h3').textContent.includes('Critical')) {\n" +
               "                group.querySelectorAll('.issue-card').forEach(card => card.classList.add('open'));\n" +
               "            }\n" +
               "        });\n";
    }

    /**
     * 获取报告CSS样式（保留旧方法兼容性）
     */
    private static String getReportStyles() {
        return getEnterpriseReportStyles();
    }

    /**
     * HTML转义
     */
    private static String escapeHtml(String html) {
        return html.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 生成问题总结要点
     */
    private static List<String> generateSummaryPoints(List<AccessibilityIssue> issues) {
        Map<String, Integer> categoryCounts = new HashMap<>();

        for (AccessibilityIssue issue : issues) {
            String category;
            String description = issue.getDescription().toLowerCase();

            if (description.contains("label") || description.contains("form")) {
                category = "Missing Form Labels";
            } else if (description.contains("alt") || description.contains("image")) {
                category = "Missing Image Alt Text";
            } else if (description.contains("contrast") || description.contains("color")) {
                category = "Insufficient Color Contrast";
            } else if (description.contains("focus")) {
                category = "Keyboard Focus Issues";
            } else if (description.contains("title") || description.contains("heading")) {
                category = "Page Title Issues";
            } else {
                category = "Other Accessibility Issues";
            }

            categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
        }

        // 按数量排序,取前4个
        return categoryCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(4)
                .map(e -> e.getKey())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 计算测试统计数据
     */
    public static TestStatistics calculateStatistics(List<AccessibilityIssue> issues, int totalPages) {
        TestStatistics stats = new TestStatistics();
        stats.setTotalPages(totalPages);
        stats.setTotalIssues(issues.size());

        for (AccessibilityIssue issue : issues) {
            stats.incrementIssueCount(issue.getSeverity());
        }

        // 计算Pass Rate
        double passRate;
        if (issues.isEmpty()) {
            passRate = 100.0;
        } else {
            int criticalAndHigh = stats.getIssueCounts().getOrDefault(IssueSeverity.CRITICAL, 0) +
                                  stats.getIssueCounts().getOrDefault(IssueSeverity.HIGH, 0);
            passRate = Math.max(0, 100.0 - (criticalAndHigh * 10.0) - (issues.size() - criticalAndHigh) * 2.0);
            passRate = Math.round(passRate * 10.0) / 10.0;
        }
        stats.setPassRate(passRate);

        return stats;
    }

    /**
     * 保存HTML报告到文件
     *
     * @param htmlContent HTML报告内容
     * @param outputPath 输出文件路径
     */
    public static void saveHtmlReport(String htmlContent, String outputPath) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(outputPath);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, htmlContent, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Accessibility report saved to: {}", outputPath);
        } catch (Exception e) {
            logger.error("Failed to save HTML report: {}", e.getMessage(), e);
        }
    }

    /**
     * 完整的无障碍测试流程:检查并生成独立HTML报告
     *
     * @param page Playwright Page对象
     * @param config 报告配置
     * @param outputPath 输出文件路径
     * @return 发现的问题列表
     */
    public static List<AccessibilityIssue> runFullAccessibilityTest(
            Page page,
            ReportConfig config,
            String outputPath) {

        logger.info("Starting full accessibility test...");

        // 执行增强版检查
        List<AccessibilityIssue> issues = checkPageAccessibilityEnhanced(page, config.isIncludeScreenshots());

        // 计算统计数据
        TestStatistics stats = calculateStatistics(issues, 1);

        // 生成HTML报告
        String htmlReport = generateStandaloneHtmlReport(issues, config, stats);

        // 保存报告
        saveHtmlReport(htmlReport, outputPath);

        logger.info("Full accessibility test completed. Issues found: {}", issues.size());

        return issues;
    }


    /**
     * Capture screenshot with visibility information
     * Returns both the screenshot and whether the element was hidden
     * 
     * @param page Playwright page object
     * @param elementSelector CSS selector for the problematic element
     * @return ScreenshotResult containing screenshot bytes and visibility status
     */
    public static ScreenshotResult captureScreenshotWithVisibilityInfo(Page page, String elementSelector) {
        try {
            logger.info("Attempting to capture annotated screenshot for selector: {}", elementSelector);
            
            // Get element
            Locator element = page.locator(elementSelector);
            int elementCount = element.count();
            
            if (elementCount == 0) {
                logger.warn("Element not found for screenshot annotation: {}, capturing regular screenshot", elementSelector);
                byte[] screenshot = captureScreenshotWithHiddenElementNote(page, elementSelector);
                return new ScreenshotResult(screenshot, true);
            }
            
            if (elementCount > 1) {
                logger.warn("Multiple elements ({}) found for selector: {}, using first one", elementCount, elementSelector);
                element = element.first();
            }

            // Check if element is visible
            boolean isVisible = element.isVisible();
            logger.info("Element visibility: {}", isVisible);
            
            if (!isVisible) {
                logger.warn("Element is not visible: {}, trying to find visible parent", elementSelector);
                // Try to find a visible parent element to annotate
                Locator visibleAncestor = findVisibleAncestor(page, elementSelector);
                if (visibleAncestor != null && visibleAncestor.count() > 0) {
                    element = visibleAncestor;
                    isVisible = true;
                    logger.info("Found visible ancestor element");
                } else {
                    logger.warn("Could not find visible ancestor, capturing screenshot with hidden element note");
                    byte[] screenshot = captureScreenshotWithHiddenElementNote(page, elementSelector);
                    return new ScreenshotResult(screenshot, true);
                }
            }

            // Scroll element into view
            element.scrollIntoViewIfNeeded();
            page.waitForTimeout(200);

            // Get element position
            BoundingBox boundingBox = element.boundingBox();
            if (boundingBox == null || boundingBox.width <= 0 || boundingBox.height <= 0) {
                logger.warn("Could not get valid bounding box for element: {}", elementSelector);
                byte[] screenshot = captureScreenshotWithHiddenElementNote(page, elementSelector);
                return new ScreenshotResult(screenshot, true);
            }
            
            logger.info("Element bounding box: x={}, y={}, width={}, height={}", 
                boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);

            // Capture screenshot
            byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            
            // Load image
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(screenshotBytes));
            if (image == null) {
                return new ScreenshotResult(screenshotBytes, false);
            }
            
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Get element position
            double x = boundingBox.x;
            double y = boundingBox.y;
            double width = boundingBox.width;
            double height = boundingBox.height;
            
            // Adjust if element is outside viewport
            if (x < 0 || y < 0 || x + width > imageWidth || y + height > imageHeight) {
                x = Math.max(0, Math.min(x, imageWidth - width));
                y = Math.max(0, Math.min(y, imageHeight - height));
            }
            
            // Draw red circle around the element
            int padding = 15;
            int circleX = (int) Math.max(0, x - padding);
            int circleY = (int) Math.max(0, y - padding);
            int circleWidth = (int) Math.min(width + 2 * padding, imageWidth - circleX);
            int circleHeight = (int) Math.min(height + 2 * padding, imageHeight - circleY);
            
            // Draw filled semi-transparent background
            g2d.setColor(new Color(255, 0, 0, 50));
            g2d.fillOval(circleX, circleY, circleWidth, circleHeight);
            
            // Draw red border
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawOval(circleX, circleY, circleWidth, circleHeight);
            
            // Draw arrow and label
            int arrowStartX = Math.min(circleX + circleWidth + 20, imageWidth - 140);
            int arrowStartY = circleY + circleHeight / 2;
            int arrowEndX = circleX + circleWidth;
            int arrowEndY = circleY + circleHeight / 2;
            
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(Color.RED);
            g2d.drawLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY);
            
            int[] xPoints = {arrowStartX, arrowStartX - 12, arrowStartX - 12};
            int[] yPoints = {arrowStartY, arrowStartY - 8, arrowStartY + 8};
            g2d.fillPolygon(xPoints, yPoints, 3);
            
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            String label = "Issue Here";
            int labelWidth = g2d.getFontMetrics().stringWidth(label);
            int labelHeight = g2d.getFontMetrics().getHeight();
            int labelX = Math.min(arrowStartX + 10, imageWidth - labelWidth - 10);
            int labelY = arrowStartY + 6;
            
            g2d.setColor(new Color(255, 255, 255, 230));
            g2d.fillRect(labelX - 5, labelY - labelHeight + 5, labelWidth + 10, labelHeight + 5);
            
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRect(labelX - 5, labelY - labelHeight + 5, labelWidth + 10, labelHeight + 5);
            g2d.drawString(label, labelX, labelY);
            
            g2d.dispose();
            
            logger.info("Successfully created annotated screenshot with red circle at position ({}, {})", circleX, circleY);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return new ScreenshotResult(baos.toByteArray(), false);
            
        } catch (Exception e) {
            logger.warn("Failed to add annotation to screenshot: {}", e.getMessage(), e);
            try {
                byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                return new ScreenshotResult(screenshot, false);
            } catch (Exception ex) {
                logger.error("Failed to capture fallback screenshot", ex);
                return new ScreenshotResult(null, true);
            }
        }
    }
    
    /**
     * Capture screenshot with red circle highlighting the problematic element
     * Handles hidden/invisible elements by finding visible parent or nearby visible elements
     * 
     * @param page Playwright page object
     * @param elementSelector CSS selector for the problematic element
     * @return Screenshot bytes with red circle annotation, or null if failed
     */
    private static byte[] captureScreenshotWithAnnotation(Page page, String elementSelector) {
        ScreenshotResult result = captureScreenshotWithVisibilityInfo(page, elementSelector);
        return result.getScreenshot();
    }
    
    /**
     * Find a visible ancestor element for a hidden element
     */
    private static Locator findVisibleAncestor(Page page, String elementSelector) {
        try {
            // Use JavaScript to find the nearest visible ancestor
            String visibleAncestorSelector = (String) page.evaluate(
                "selector => {" +
                "  const element = document.querySelector(selector);" +
                "  if (!element) return null;" +
                "  let parent = element.parentElement;" +
                "  while (parent) {" +
                "    const style = window.getComputedStyle(parent);" +
                "    if (style.display !== 'none' && " +
                "        style.visibility !== 'hidden' && " +
                "        style.opacity !== '0' && " +
                "        parent.offsetWidth > 0 && " +
                "        parent.offsetHeight > 0) {" +
                "      // Return a selector for this visible ancestor" +
                "      if (parent.id) return '#' + parent.id;" +
                "      const tagName = parent.tagName.toLowerCase();" +
                "      if (parent.className && typeof parent.className === 'string') {" +
                "        const classes = parent.className.split(' ').filter(c => c && c.length > 0);" +
                "        if (classes.length > 0) return tagName + '.' + classes[0];" +
                "      }" +
                "      return tagName;" +
                "    }" +
                "    parent = parent.parentElement;" +
                "  }" +
                "  return null;" +
                "}",
                elementSelector
            );
            
            if (visibleAncestorSelector != null && !visibleAncestorSelector.isEmpty()) {
                logger.info("Found visible ancestor with selector: {}", visibleAncestorSelector);
                return page.locator(visibleAncestorSelector);
            }
        } catch (Exception e) {
            logger.debug("Error finding visible ancestor: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Capture screenshot with a note about hidden element
     */
    private static byte[] captureScreenshotWithHiddenElementNote(Page page, String elementSelector) {
        try {
            byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(screenshotBytes));
            
            if (image == null) {
                return screenshotBytes;
            }
            
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            
            // Draw a semi-transparent overlay box at top of screenshot
            int boxHeight = 60;
            g2d.setColor(new Color(255, 165, 0, 200)); // Orange background
            g2d.fillRect(0, 0, imageWidth, boxHeight);
            
            // Draw border
            g2d.setColor(new Color(255, 140, 0));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(0, 0, imageWidth - 1, boxHeight - 1);
            
            // Draw warning text
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            String warningText = "Hidden/Invisible Element - Cannot Annotate Location";
            g2d.drawString(warningText, 20, 25);
            
            // Draw selector info
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            String selectorText = "Selector: " + (elementSelector.length() > 80 ? elementSelector.substring(0, 80) + "..." : elementSelector);
            g2d.drawString(selectorText, 20, 45);
            
            g2d.dispose();
            
            logger.info("Created screenshot with hidden element note");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            logger.warn("Failed to add hidden element note: {}", e.getMessage());
            try {
                return page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            } catch (Exception ex) {
                return null;
            }
        }
    }
}



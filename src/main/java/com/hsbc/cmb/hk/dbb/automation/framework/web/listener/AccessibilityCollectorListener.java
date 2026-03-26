package com.hsbc.cmb.hk.dbb.automation.framework.web.listener;

import com.hsbc.cmb.hk.dbb.automation.framework.web.accessibility.AccessibilityScanner;
import net.thucydides.model.steps.StepListener;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener to automatically manage AccessibilityScanner lifecycle
 * Initializes collector at test suite start and generates final report at test suite end
 * Users no longer need to manually call initialize() and cleanup()
 */
public class AccessibilityCollectorListener implements StepListener {
    
    private static final Logger logger = LoggerFactory.getLogger(AccessibilityCollectorListener.class);
    
    private static volatile boolean initialized = false;
    
    @Override
    public void testSuiteStarted(Class<?> testClass) {
        if (!initialized) {
            try {
                AccessibilityScanner.initialize();
                initialized = true;
                logger.info("AccessibilityScanner automatically initialized for test suite: {}", testClass.getName());
            } catch (Exception e) {
                logger.error("Failed to initialize AccessibilityScanner", e);
            }
        }
    }
    
    @Override
    public void testSuiteStarted(Story story) {
        if (!initialized) {
            try {
                AccessibilityScanner.initialize();
                initialized = true;
                logger.info("AccessibilityScanner automatically initialized for story: {}", story.getStoryName());
            } catch (Exception e) {
                logger.error("Failed to initialize AccessibilityScanner", e);
            }
        }
    }
    
    @Override
    public void testSuiteFinished() {
        if (initialized) {
            try {
                logger.info("Generating final accessibility report...");
                AccessibilityScanner.generateFinalReport();
                logger.info("Final accessibility report generated successfully");
                
                AccessibilityScanner.cleanup();
                logger.info("AccessibilityScanner cleaned up");
                
                initialized = false;
            } catch (Exception e) {
                logger.error("Failed to generate final accessibility report or cleanup", e);
            }
        }
    }
    
    // Other StepListener methods (no-op implementations)
    @Override public void testStarted(String testName) {}
    @Override public void testFinished(TestOutcome result) {}
    @Override public void testSkipped() {}
    @Override public void stepStarted(net.thucydides.model.steps.ExecutedStepDescription step) {}
    @Override public void stepFinished() {}
    @Override public void stepFailed(net.thucydides.model.steps.StepFailure failure) {}
    @Override public void lastStepFailed(net.thucydides.model.steps.StepFailure failure) {}
    @Override public void stepIgnored() {}
    @Override public void testStarted(String testName, String testMethod) {}
    @Override public void testStarted(String testName, String testMethod, java.time.ZonedDateTime startTime) {}
    @Override public void testFinished(TestOutcome result, boolean isInDataDrivenTest, java.time.ZonedDateTime finishTime) {}
    @Override public void testRetried() {}
    @Override public void skippedStepStarted(net.thucydides.model.steps.ExecutedStepDescription step) {}
    @Override public void stepFinished(java.util.List<net.thucydides.model.screenshots.ScreenshotAndHtmlSource> screenshots) {}
    @Override public void stepFinished(java.util.List<net.thucydides.model.screenshots.ScreenshotAndHtmlSource> screenshots, java.time.ZonedDateTime timestamp) {}
    @Override public void stepFailed(net.thucydides.model.steps.StepFailure failure, java.util.List<net.thucydides.model.screenshots.ScreenshotAndHtmlSource> screenshots, boolean takeScreenshotOnFailure, java.time.ZonedDateTime timestamp) {}
    @Override public void takeScreenshots(java.util.List<net.thucydides.model.screenshots.ScreenshotAndHtmlSource> screenshots) {}
    @Override public void takeScreenshots(net.thucydides.model.domain.TestResult result, java.util.List<net.thucydides.model.screenshots.ScreenshotAndHtmlSource> screenshots) {}
    @Override public void stepPending() {}
    @Override public void stepPending(String description) {}
    @Override public void testFailed(TestOutcome result, Throwable throwable) {}
    @Override public void testIgnored() {}
    @Override public void testPending() {}
    @Override public void testIsManual() {}
    @Override public void notifyScreenChange() {}
    @Override public void useExamplesFrom(net.thucydides.model.domain.DataTable dataTable) {}
    @Override public void addNewExamplesFrom(net.thucydides.model.domain.DataTable dataTable) {}
    @Override public void exampleStarted(java.util.Map<String, String> data) {}
    @Override public void exampleFinished() {}
    @Override public void assumptionViolated(String message) {}
    @Override public void testRunFinished() {}
}

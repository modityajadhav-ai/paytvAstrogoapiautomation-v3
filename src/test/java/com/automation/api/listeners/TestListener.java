package com.automation.api.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG lifecycle hooks for structured logging (Logback) and Extent HTML report.
 */
public class TestListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onStart(ITestContext context) {
        LOG.info("Starting suite: {}", context.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        String className  = result.getMethod().getRealClass().getSimpleName();
        String methodName = result.getName();
        LOG.info("Test start: {}.{}", className, methodName);

        // Get or create the parent node for the class, then create a child node for this method.
        ExtentTest classNode  = ExtentReportManager.getOrCreateClassNode(className);
        ExtentTest methodNode = classNode.createNode(methodName);
        ExtentReportManager.setCurrentTest(methodNode);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        long duration = result.getEndMillis() - result.getStartMillis();
        LOG.info("Test passed: {} ({} ms)", result.getName(), duration);

        ExtentTest test = ExtentReportManager.getCurrentTest();
        if (test != null) {
            test.log(Status.PASS, "Test passed in " + duration + " ms");
        }
        ExtentReportManager.removeCurrentTest();

        ExcelReportManager.addResult(new ExcelReportManager.TestRecord(
                result.getMethod().getRealClass().getSimpleName(),
                result.getName(),
                "PASS",
                duration,
                null
        ));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable t = result.getThrowable();
        long duration = result.getEndMillis() - result.getStartMillis();
        LOG.error("Test failed: {} — {}", result.getName(), t != null ? t.getMessage() : "unknown");

        ExtentTest test = ExtentReportManager.getCurrentTest();
        if (test != null) {
            if (t != null) {
                test.log(Status.FAIL, t);
            } else {
                test.log(Status.FAIL, "Test failed with unknown cause");
            }
        }
        ExtentReportManager.removeCurrentTest();

        ExcelReportManager.addResult(new ExcelReportManager.TestRecord(
                result.getMethod().getRealClass().getSimpleName(),
                result.getName(),
                "FAIL",
                duration,
                t != null ? t.getMessage() : "Unknown failure"
        ));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Throwable t = result.getThrowable();
        long duration = result.getEndMillis() - result.getStartMillis();
        LOG.warn("Test skipped: {}", result.getName());

        ExtentTest test = ExtentReportManager.getCurrentTest();
        if (test != null) {
            test.log(Status.SKIP, t != null ? t.getMessage() : "Test skipped");
        }
        ExtentReportManager.removeCurrentTest();

        ExcelReportManager.addResult(new ExcelReportManager.TestRecord(
                result.getMethod().getRealClass().getSimpleName(),
                result.getName(),
                "SKIP",
                duration,
                t != null ? t.getMessage() : null
        ));
    }

    @Override
    public void onFinish(ITestContext context) {
        LOG.info("Finished suite: {}", context.getName());
        ExtentReportManager.getInstance().flush();
        LOG.info("Extent report written → target/extent-reports/ExtentReport.html");
        ExcelReportManager.flush();
    }
}

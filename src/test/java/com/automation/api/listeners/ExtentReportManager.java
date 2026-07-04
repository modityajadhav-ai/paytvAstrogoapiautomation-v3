package com.automation.api.listeners;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that owns the ExtentReports instance for the entire test run.
 * The SparkReporter produces a single self-contained HTML file that can be
 * attached to an email or uploaded without any extra assets.
 *
 * Report structure:
 *   ClassName (parent)
 *     └── testMethodName (child node)
 *
 * Report path: target/extent-reports/ExtentReport.html
 */
public class ExtentReportManager {

    private static final String REPORT_PATH = "target/extent-reports/ExtentReport.html";

    private static final ExtentReports INSTANCE;

    /** One parent node per test class, created lazily and shared across threads. */
    private static final ConcurrentHashMap<String, ExtentTest> CLASS_NODES = new ConcurrentHashMap<>();

    /** Holds the child ExtentTest (test method node) for the currently running test. */
    private static final ThreadLocal<ExtentTest> CURRENT_TEST = new ThreadLocal<>();

    static {
        ExtentSparkReporter spark = new ExtentSparkReporter(REPORT_PATH);
        spark.config().setTheme(Theme.DARK);
        spark.config().setDocumentTitle("API Automation Report");
        spark.config().setReportName("API Test Execution Report");
        spark.config().setEncoding("UTF-8");

        INSTANCE = new ExtentReports();
        INSTANCE.attachReporter(spark);
        INSTANCE.setSystemInfo("Framework", "REST Assured + TestNG");
        INSTANCE.setSystemInfo("Java Version", System.getProperty("java.version"));
        INSTANCE.setSystemInfo("OS", System.getProperty("os.name"));
    }

    private ExtentReportManager() {}

    public static ExtentReports getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the parent ExtentTest node for the given class name,
     * creating it once if it does not yet exist.
     */
    public static ExtentTest getOrCreateClassNode(String className) {
        return CLASS_NODES.computeIfAbsent(className, INSTANCE::createTest);
    }

    public static void setCurrentTest(ExtentTest test) {
        CURRENT_TEST.set(test);
    }

    public static ExtentTest getCurrentTest() {
        return CURRENT_TEST.get();
    }

    public static void removeCurrentTest() {
        CURRENT_TEST.remove();
    }
}

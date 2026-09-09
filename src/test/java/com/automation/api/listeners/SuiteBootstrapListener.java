package com.automation.api.listeners;

import com.automation.api.base.BaseTest;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Runs once before the suite without registering as a TestNG/Allure test case
 * (unlike {@code @BeforeSuite} on {@link BaseTest}, which Allure lists as {@code suiteSetup}).
 */
public final class SuiteBootstrapListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        BaseTest.bootstrapSuite();
    }
}

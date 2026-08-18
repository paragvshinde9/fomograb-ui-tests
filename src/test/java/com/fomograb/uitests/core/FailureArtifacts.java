package com.fomograb.uitests.core;

import com.fomograb.uitests.config.TestConfig;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Saves a screenshot and the Playwright trace (viewable at
 * https://trace.playwright.dev) for a failing test, next to the surefire
 * output, and attaches both to the Allure report so a CI failure is
 * debuggable without re-running anything. Called from
 * {@link BaseTest#tearDown} — kept as its own class purely so BaseTest reads
 * as "the lifecycle" and this reads as "what happens on failure."
 */
final class FailureArtifacts {

    private static final Logger log = LoggerFactory.getLogger(FailureArtifacts.class);

    private FailureArtifacts() {
    }

    static void capture(Page page, BrowserContext context, ITestResult result) {
        String safeName = result.getTestClass().getRealClass().getSimpleName()
            + "_" + result.getMethod().getMethodName();
        Path dir = Path.of(TestConfig.artifactsDir(), safeName);

        capturePageScreenshot(page, dir);
        captureTrace(context, dir);
    }

    private static void capturePageScreenshot(Page page, Path dir) {
        try {
            Files.createDirectories(dir);
            byte[] bytes = page.screenshot(new Page.ScreenshotOptions()
                .setPath(dir.resolve("failure.png"))
                .setFullPage(true));
            Allure.addAttachment("Screenshot on failure", new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("Could not capture failure screenshot", e);
        }
    }

    private static void captureTrace(BrowserContext context, Path dir) {
        try {
            Path tracePath = dir.resolve("trace.zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            Optional.of(tracePath)
                .filter(Files::exists)
                .ifPresent(p -> {
                    try {
                        Allure.addAttachment("Playwright trace (open at trace.playwright.dev)",
                            "application/zip", Files.newInputStream(p), ".zip");
                    } catch (Exception e) {
                        log.warn("Could not attach trace to Allure", e);
                    }
                });
        } catch (Exception e) {
            log.warn("Could not stop/save Playwright trace", e);
        }
    }
}

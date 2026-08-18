package com.fomograb.uitests.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Resolves the {@code browser} config value ("chromium" | "firefox" | "webkit")
 * to a launched {@link Browser}. Chromium is the default: it is what most of
 * fomograb's users run and what CI installs by default via
 * {@code exec:java@install-browsers}.
 */
final class Browsers {

    private Browsers() {
    }

    static Browser launch(Playwright playwright, String browserName, boolean headless, double slowMoMillis) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
            .setHeadless(headless)
            .setSlowMo(slowMoMillis);
        return switch (browserName.toLowerCase()) {
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit" -> playwright.webkit().launch(options);
            case "chromium" -> playwright.chromium().launch(options);
            default -> throw new IllegalArgumentException(
                "Unknown browser '" + browserName + "'. Use chromium, firefox, or webkit.");
        };
    }
}

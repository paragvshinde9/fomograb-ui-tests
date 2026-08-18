package com.fomograb.uitests.pages;

import com.microsoft.playwright.Page;

/**
 * Every page object wraps the single {@link Page} for the test that created it.
 * Locators are built lazily in each subclass's methods (not cached as fields)
 * since Playwright locators are just unresolved queries — building them fresh
 * means they always match current DOM state, no staleness handling needed.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }
}

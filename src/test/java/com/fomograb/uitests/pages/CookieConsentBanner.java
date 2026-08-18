package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * {@code src/components/CookieConsent.tsx} — a plain, unstyled-class {@code <div>}
 * with two {@code <button>}s, shown ~1.5s after first paint only when
 * {@code localStorage['fg_cookie_consent']} is unset. Most tests just want it
 * gone (see {@code BaseTest#goTo}); this page object exists for the one test
 * that verifies the banner's own accept/decline behaviour and persistence.
 */
public class CookieConsentBanner extends BasePage {

    public CookieConsentBanner(Page page) {
        super(page);
    }

    public Locator acceptAllButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Accept All"));
    }

    public Locator necessaryOnlyButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Necessary Only"));
    }

    public boolean isVisible() {
        return acceptAllButton().isVisible();
    }
}

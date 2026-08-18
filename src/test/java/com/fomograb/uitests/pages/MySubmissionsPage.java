package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /my-submissions} — {@code src/components/MySubmissions.tsx}, auth-gated.
 * Lists the current user's community deal submissions with a status badge
 * (pending/approved/rejected).
 */
public class MySubmissionsPage extends BasePage {

    public MySubmissionsPage(Page page) {
        super(page);
    }

    public Locator submissionCard(String nameSubstring) {
        return page.locator(".submission-card").filter(new Locator.FilterOptions().setHasText(nameSubstring));
    }

    public Locator statusBadge(String nameSubstring) {
        return submissionCard(nameSubstring).locator(".submission-badge");
    }
}

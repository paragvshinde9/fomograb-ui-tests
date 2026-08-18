package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /wishlist} — {@code src/components/WishlistPage.tsx}. No auth required:
 * it just reads the {@code wishlistProductIds} array out of localStorage and
 * bulk-fetches those products, so tests can seed it directly (see
 * {@link #seedIds(Page, String...)}) instead of clicking through the UI first.
 */
public class WishlistPage extends BasePage {

    public WishlistPage(Page page) {
        super(page);
    }

    /** Writes the localStorage key WishlistPage reads, before it's ever opened. Call before navigating. */
    public static void seedIds(Page page, String... productIds) {
        String json = "[" + String.join(",", java.util.Arrays.stream(productIds)
            .map(id -> "\"" + id + "\"").toArray(String[]::new)) + "]";
        page.addInitScript("window.localStorage.setItem('wishlistProductIds', '" + json + "');");
    }

    public Locator emptyState() {
        return page.locator(".wl-empty");
    }

    public Locator itemCount() {
        return page.locator(".wl-count");
    }

    public Locator cards() {
        return page.locator(".wl-card");
    }

    public Locator card(String nameSubstring) {
        return cards().filter(new Locator.FilterOptions().setHasText(nameSubstring));
    }

    /** Confirms the native window.confirm() dialog WishlistPage.tsx opens before clearing. */
    public WishlistPage clearAll() {
        page.onceDialog(dialog -> dialog.accept());
        page.locator(".wl-clear-btn").click();
        return this;
    }

    public WishlistPage removeCard(String nameSubstring) {
        card(nameSubstring).locator(".wl-remove-btn").click();
        return this;
    }
}

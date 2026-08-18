package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

/**
 * The homepage ({@code /}), rendered by {@code src/components/Dashboard.tsx}.
 * It stays mounted at all times behind product/category detail routes
 * (see App.tsx's {@code DashboardLayout}), but this page object only models
 * the parts relevant to browsing: search, category navigation, and the
 * product grid with its "Load More" pager.
 */
public class DashboardPage extends BasePage {

    public DashboardPage(Page page) {
        super(page);
    }

    // ── Search ──────────────────────────────────────────────────────────────

    public Locator searchInput() {
        return page.getByPlaceholder("Search by product, brand, category");
    }

    public Locator clearSearchButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Clear search"));
    }

    /** Types the query and submits via Enter, then waits for the results grid to settle. */
    public DashboardPage search(String query) {
        searchInput().click();
        searchInput().fill(query);
        searchInput().press("Enter");
        page.waitForURL(url -> url.contains("q=") || url.contains("search="),
            new Page.WaitForURLOptions().setTimeout(10000));
        return this;
    }

    // ── Category navigation ─────────────────────────────────────────────────

    private Locator categoryDropdownButton() {
        return page.locator(".cat-drop-btn");
    }

    private Locator categoryPanel() {
        return page.locator(".cat-drop-panel");
    }

    public DashboardPage openCategoryMenu() {
        categoryDropdownButton().click();
        categoryPanel().waitFor();
        return this;
    }

    /** The trigger button is a toggle (Dashboard.tsx: {@code setCatDropOpen(o => !o)}), so this reuses it to close. */
    public DashboardPage closeCategoryMenu() {
        categoryDropdownButton().click();
        categoryPanel().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
        return this;
    }

    /** Clicks a top-level category (e.g. "Electronics") from the already-open dropdown. */
    public DashboardPage selectCategory(String categoryLabel) {
        page.locator(".cat-drop-item-btn").filter(new Locator.FilterOptions().setHasText(categoryLabel))
            .first()
            .click();
        return this;
    }

    public boolean isCategoryMenuExpanded() {
        return "true".equals(categoryDropdownButton().getAttribute("aria-expanded"));
    }

    // ── Product grid ────────────────────────────────────────────────────────

    private Locator productCards() {
        return page.locator(".product-card");
    }

    public Locator productCard(String nameSubstring) {
        return productCards().filter(new Locator.FilterOptions().setHasText(nameSubstring));
    }

    public Locator firstProductCard() {
        return productCards().first();
    }

    public int visibleProductCount() {
        return productCards().count();
    }

    /** Reads the name off the first card, clicks it, and returns the name for the caller to assert against on the detail page. */
    public String openFirstProduct() {
        Locator firstCard = firstProductCard();
        String name = firstCard.locator("h5").innerText();
        firstCard.click();
        page.waitForURL(Pattern.compile(".*/product/.+"));
        return name;
    }

    public DashboardPage openProduct(String nameSubstring) {
        productCard(nameSubstring).first().click();
        page.waitForURL(Pattern.compile(".*/product/.+"));
        return this;
    }

    // ── Pagination ("Load More") ────────────────────────────────────────────

    public Locator loadMoreButton() {
        return page.locator(".load-more-btn");
    }

    public Locator loadMoreCount() {
        return page.locator(".load-more-count");
    }

    public DashboardPage loadMore() {
        Locator button = loadMoreButton();
        int before = visibleProductCount();
        button.click();
        page.waitForCondition(() -> visibleProductCount() > before);
        return this;
    }

    // ── Session (header) ────────────────────────────────────────────────────

    /** Only rendered when {@code user} is set — see App.tsx's {@code isAdminView}/gating logic. */
    public Locator logoutButton() {
        return page.getByTitle("Sign out of your account");
    }

    /** Only rendered for an anonymous visitor. */
    public Locator loginHeaderButton() {
        return page.getByTitle("Log in to your account");
    }

    public boolean isLoggedIn() {
        return logoutButton().isVisible();
    }

    public void logout() {
        logoutButton().click();
        // Dashboard.tsx swaps the header buttons synchronously on logout — no navigation to wait on.
        loginHeaderButton().waitFor();
    }

    // ── Preferences menu (hamburger) ────────────────────────────────────────

    public Locator preferencesToggle() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Preferences"));
    }

    public DashboardPage openPreferencesMenu() {
        preferencesToggle().click();
        page.locator(".prefs-dropdown").waitFor();
        return this;
    }

    /** Toggles light/dark mode from the preferences dropdown; caller asserts on {@code <html data-theme>}. */
    public DashboardPage toggleDarkMode() {
        openPreferencesMenu();
        page.locator(".prefs-option").filter(new Locator.FilterOptions().setHasText(Pattern.compile("Mode$")))
            .click();
        return this;
    }

    public String currentColorTheme() {
        return page.locator("html").getAttribute("data-theme");
    }
}

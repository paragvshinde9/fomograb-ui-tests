package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /product/:slug} — {@code src/components/ProductDetail.tsx}, lazy-loaded.
 * There is no cart/checkout in FomoGrab (it's a deals aggregator, not a
 * storefront): the primary call to action is an outbound affiliate link that
 * opens in a new tab, which {@link #clickBuyAndCaptureOutboundUrl()} models
 * without actually depending on the third-party marketplace page.
 */
public class ProductDetailPage extends BasePage {

    public ProductDetailPage(Page page) {
        super(page);
    }

    public Locator title() {
        return page.locator(".product-info-panel h1");
    }

    public Locator breadcrumb() {
        return page.locator(".pd-breadcrumb");
    }

    public Locator wishlistButton() {
        return page.locator(".pd-wishlist-btn");
    }

    public boolean isWishlisted() {
        String label = wishlistButton().getAttribute("aria-label");
        return "Remove from wishlist".equals(label);
    }

    public ProductDetailPage toggleWishlist() {
        boolean before = isWishlisted();
        wishlistButton().click();
        page.waitForCondition(() -> isWishlisted() != before);
        return this;
    }

    public Locator voteUpButton() {
        return page.locator(".vote-btn.up");
    }

    public Locator voteDownButton() {
        return page.locator(".vote-btn.down");
    }

    /** Tab labels carry emoji + dynamic counts ("💬 Comments (3)"), so callers match on the bare word. */
    public Locator tab(String label) {
        return page.locator(".pd-tab").filter(new Locator.FilterOptions().setHasText(label));
    }

    public ProductDetailPage openTab(String label) {
        tab(label).click();
        return this;
    }

    public boolean isTabActive(String label) {
        String classes = tab(label).getAttribute("class");
        return classes != null && classes.contains("active");
    }

    public Locator primaryBuyButton() {
        return page.locator(".primary-buy-btn");
    }

    public Locator stickyBuyBar() {
        return page.locator(".sticky-buy-bar");
    }

    /**
     * Buy button href — usually the marketplace's own affiliate-tagged URL,
     * occasionally our own {@code /api/go/:offerId} redirect when an offer has
     * no direct link (see {@code buildBuyHref} in ProductDetail.tsx). Tests
     * assert on this attribute rather than clicking through: the target is a
     * real third-party checkout page (Amazon/Walmart/eBay/...), and firing
     * live requests at those affiliate links on every CI run would be both
     * flaky and impolite to the marketplace.
     */
    public String buyButtonHref() {
        return primaryBuyButton().getAttribute("href");
    }

    public boolean buyButtonOpensNewTab() {
        return "_blank".equals(primaryBuyButton().getAttribute("target"));
    }
}

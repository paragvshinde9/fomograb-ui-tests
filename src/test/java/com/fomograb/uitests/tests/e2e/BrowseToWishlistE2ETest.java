package com.fomograb.uitests.tests.e2e;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.DashboardPage;
import com.fomograb.uitests.pages.ProductDetailPage;
import com.fomograb.uitests.pages.WishlistPage;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * A full guest shopping journey in one continuous browser session, as opposed
 * to the single-feature tests in {@code tests.product}/{@code tests.wishlist}
 * that each check one behaviour in isolation. This is deliberately written as
 * one long {@code @Test} method rather than several small ones: BaseTest gives
 * every {@code @Test} method its own fresh {@link com.microsoft.playwright.BrowserContext},
 * so splitting a journey across methods would just lose the session between
 * "steps" — the whole point of an E2E test is that it's one uninterrupted trip
 * through the app.
 */
@Test(groups = {Tags.REGRESSION, Tags.E2E})
public class BrowseToWishlistE2ETest extends BaseTest {

    @Test(description = "Guest: search for a deal, read through its tabs, wishlist it, find it on /wishlist, then remove it")
    public void guestCanBrowseWishlistAndUnwishlistAProduct() {
        // 1. Land on the homepage and find a real product to chase — see
        //    SearchAndBrowseTest for why this suite reads live data instead of
        //    a hardcoded fixture name.
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.firstProductCard()).isVisible();
        String productName = dashboard.firstProductCard().locator("h5").innerText();
        String keyword = productName.split("\\s+")[0];

        // 2. Search for it instead of just clicking the first card — exercises
        //    the search round-trip as part of the journey.
        dashboard.search(keyword);
        assertThat(dashboard.productCard(keyword)).isVisible();
        dashboard.openProduct(keyword);

        // 3. On the detail page: confirm we landed in the right place, then
        //    read through Prices and History before deciding to wishlist it —
        //    a shopper comparing offers before committing.
        ProductDetailPage detail = new ProductDetailPage(page);
        assertThat(detail.title()).containsText(keyword);
        detail.openTab("Prices");
        Assert.assertTrue(detail.isTabActive("Prices"));
        detail.openTab("History");
        Assert.assertTrue(detail.isTabActive("History"));

        // 4. Wishlist it.
        Assert.assertFalse(detail.isWishlisted(), "Fresh context — nothing should be pre-wishlisted");
        detail.toggleWishlist();
        Assert.assertTrue(detail.isWishlisted());

        // 5. Confirm it shows up on the wishlist page, then follow "View Deal"
        //    back to the product — a round trip a real visitor would make.
        goTo("/wishlist");
        WishlistPage wishlist = new WishlistPage(page);
        assertThat(wishlist.card(productName)).isVisible();
        wishlist.card(productName).locator(".wl-view-btn").click();
        page.waitForURL(url -> url.contains("/product/"));
        assertThat(new ProductDetailPage(page).title()).containsText(keyword);

        // 6. Changed their mind — remove it, and confirm the wishlist is empty again.
        goTo("/wishlist");
        wishlist.removeCard(productName);
        assertThat(wishlist.emptyState()).isVisible();
    }
}

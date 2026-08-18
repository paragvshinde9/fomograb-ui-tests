package com.fomograb.uitests.tests.wishlist;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.DashboardPage;
import com.fomograb.uitests.pages.ProductDetailPage;
import com.fomograb.uitests.pages.WishlistPage;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * WishlistPage.tsx keys entirely off a {@code wishlistProductIds} array in
 * localStorage, resolved server-side by ID — there's no way to seed a valid
 * entry without a real product ID, and the product cards don't expose one in
 * the DOM. So instead of faking localStorage, these tests go through the real
 * "add from the product page" flow first, the same way a visitor would.
 */
@Test(groups = Tags.REGRESSION)
public class WishlistTest extends BaseTest {

    @Test(description = "A brand-new session with nothing saved shows the empty state")
    public void emptyWishlistShowsEmptyState() {
        goTo("/wishlist");
        WishlistPage wishlist = new WishlistPage(page);
        assertThat(wishlist.emptyState()).isVisible();
    }

    @Test(description = "Removing a single item takes it back to the empty state (when it was the only one)")
    public void removeButtonRemovesTheItem() {
        goTo("/");
        String productName = new DashboardPage(page).openFirstProduct();
        new ProductDetailPage(page).toggleWishlist();

        goTo("/wishlist");
        WishlistPage wishlist = new WishlistPage(page);
        assertThat(wishlist.card(productName)).isVisible();

        wishlist.removeCard(productName);
        assertThat(wishlist.card(productName)).not().isVisible();
        assertThat(wishlist.emptyState()).isVisible();
    }

    @Test(description = "Clear All empties the wishlist after confirming the browser dialog")
    public void clearAllEmptiesWishlist() {
        goTo("/");
        String productName = new DashboardPage(page).openFirstProduct();
        new ProductDetailPage(page).toggleWishlist();

        goTo("/wishlist");
        WishlistPage wishlist = new WishlistPage(page);
        assertThat(wishlist.card(productName)).isVisible();

        wishlist.clearAll();
        assertThat(wishlist.emptyState()).isVisible();
        Assert.assertEquals(wishlist.cards().count(), 0);
    }
}

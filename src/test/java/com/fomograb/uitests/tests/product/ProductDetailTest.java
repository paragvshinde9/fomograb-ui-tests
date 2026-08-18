package com.fomograb.uitests.tests.product;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.DashboardPage;
import com.fomograb.uitests.pages.ProductDetailPage;
import com.fomograb.uitests.pages.WishlistPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * ProductDetail.tsx, reached by clicking whatever the homepage's first live
 * card happens to be — see {@link com.fomograb.uitests.tests.browse.SearchAndBrowseTest}
 * for why these tests read real catalog data instead of assuming a fixture.
 */
@Test(groups = Tags.REGRESSION)
public class ProductDetailTest extends BaseTest {

    @Test(description = "Opening a product card lands on its detail page with a matching title and breadcrumb")
    public void productPageShowsMatchingTitleAndBreadcrumb() {
        goTo("/");
        String cardName = new DashboardPage(page).openFirstProduct();

        ProductDetailPage detail = new ProductDetailPage(page);
        Assert.assertEquals(detail.title().innerText().trim(), cardName.trim());
        Assert.assertTrue(detail.breadcrumb().isVisible());
    }

    @Test(description = "All four content tabs (Overview/Prices/History/Comments) switch the active tab")
    public void allTabsAreClickableAndBecomeActive() {
        goTo("/");
        new DashboardPage(page).openFirstProduct();
        ProductDetailPage detail = new ProductDetailPage(page);

        for (String label : new String[] {"Overview", "Prices", "History", "Comments"}) {
            detail.openTab(label);
            Assert.assertTrue(detail.isTabActive(label), "Expected the '" + label + "' tab to be active after clicking it");
        }
    }

    @Test(description = "Wishlisting from the product page is reflected on /wishlist")
    public void wishlistingFromDetailPagePersistsToWishlistPage() {
        goTo("/");
        String cardName = new DashboardPage(page).openFirstProduct();
        ProductDetailPage detail = new ProductDetailPage(page);

        Assert.assertFalse(detail.isWishlisted(), "Test expects a clean context with nothing pre-wishlisted");
        detail.toggleWishlist();
        Assert.assertTrue(detail.isWishlisted());

        goTo("/wishlist");
        WishlistPage wishlist = new WishlistPage(page);
        Assert.assertTrue(wishlist.card(cardName).isVisible(), "Expected '" + cardName + "' to appear on the wishlist page");
    }

    @Test(description = "The primary buy button, when present, is a real new-tab link (not a dead '#')")
    public void primaryBuyButtonHasValidOutboundLink() {
        goTo("/");
        new DashboardPage(page).openFirstProduct();
        ProductDetailPage detail = new ProductDetailPage(page);

        if (!detail.primaryBuyButton().isVisible()) {
            throw new SkipException("This product has no priced offer, so ProductDetail.tsx renders no buy button.");
        }
        String href = detail.buyButtonHref();
        Assert.assertNotNull(href);
        Assert.assertNotEquals(href, "#", "safeHref() falls back to '#' for an unsafe/missing URL");
        Assert.assertTrue(detail.buyButtonOpensNewTab(), "Buy button should open in a new tab (target=_blank)");
    }
}

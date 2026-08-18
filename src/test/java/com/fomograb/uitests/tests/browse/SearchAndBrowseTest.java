package com.fomograb.uitests.tests.browse;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.DashboardPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * These tests deliberately don't hardcode a product name or category: instead
 * they read whatever the first live card on the homepage actually is and
 * search/filter for that. That makes them pass against any real (staging or
 * prod-mirrored) catalog instead of depending on a specific seeded fixture —
 * the tradeoff is they need at least one real product in the database to run.
 */
@Test(groups = Tags.REGRESSION)
public class SearchAndBrowseTest extends BaseTest {

    @Test(description = "Searching by a word from a real product's name returns that product")
    public void searchingByProductNameReturnsMatchingResult() {
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.firstProductCard()).isVisible();

        String fullName = dashboard.firstProductCard().locator("h5").innerText();
        String keyword = fullName.split("\\s+")[0];

        dashboard.search(keyword);

        assertThat(dashboard.productCard(keyword)).isVisible();
    }

    @Test(description = "Clearing the search box empties the query field")
    public void clearSearchButtonEmptiesQuery() {
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.firstProductCard()).isVisible();

        String fullName = dashboard.firstProductCard().locator("h5").innerText();
        dashboard.search(fullName.split("\\s+")[0]);

        dashboard.clearSearchButton().click();
        assertThat(dashboard.searchInput()).hasValue("");
    }

    @Test(description = "Opening the category dropdown lists at least one category and reflects selection")
    public void categoryDropdownSelectionUpdatesTrigger() {
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);

        dashboard.openCategoryMenu();
        Assert.assertTrue(dashboard.isCategoryMenuExpanded(), "aria-expanded should flip to true when the panel opens");

        String firstCategoryLabel = page.locator(".cat-drop-item-btn .cat-drop-label").first().innerText();
        dashboard.selectCategory(firstCategoryLabel);

        assertThat(page.locator(".cat-drop-btn")).containsText(firstCategoryLabel);
    }

    @Test(description = "Load More appends additional cards without losing the ones already shown")
    public void loadMoreAppendsCards() {
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.firstProductCard()).isVisible();

        // Small/staging catalogs may fit on one page — nothing to paginate, so skip rather than fail.
        if (!dashboard.loadMoreButton().isVisible()) {
            throw new SkipException("No 'Load More' button — the catalog fits on a single page.");
        }

        int before = dashboard.visibleProductCount();
        dashboard.loadMore();

        Assert.assertTrue(dashboard.visibleProductCount() > before,
            "Expected more cards after Load More than the " + before + " already on screen");
    }
}

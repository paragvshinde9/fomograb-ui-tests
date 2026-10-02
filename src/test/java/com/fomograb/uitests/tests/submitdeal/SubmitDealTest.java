package com.fomograb.uitests.tests.submitdeal;

import com.fomograb.uitests.config.TestUsers;
import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.core.api.ApiSessions;
import com.fomograb.uitests.pages.MySubmissionsPage;
import com.fomograb.uitests.pages.SubmitDealPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * SubmitDeal.tsx, gated behind login by App.tsx. Every run submits a
 * uniquely-named deal (timestamp suffix) rather than reusing a fixed name —
 * this hits the real, non-mocked {@code POST /community/submit} endpoint and
 * the deal stays in the database afterwards, so re-using one name across runs
 * would just accumulate confusing duplicates for whoever moderates submissions.
 *
 * <p><b>Submissions are not cleaned up, and currently cannot be:</b> the backend
 * exposes no "delete my submission" route — moderation
 * ({@code PUT /community/:id/moderate}) is admin-only. Clearing these out needs
 * either an admin session or direct database access, neither of which this suite
 * has by design.
 *
 * <h2>Authentication</h2>
 *
 * These tests need to <i>be</i> signed in; they are not testing the login form
 * (that is {@code LoginTest}'s job, through the real UI). So the session is
 * minted over HTTP by {@link ApiSessions} and seeded into the browser context
 * before the first navigation — no login form, no Turnstile widget, and the
 * submit-deal flow starts on the page it actually cares about.
 */
@Test(groups = Tags.AUTH)
public class SubmitDealTest extends BaseTest {

    /**
     * Called once per test method by {@code BaseTest}, and deliberately mints a
     * <b>fresh</b> session each time rather than caching one for the class.
     *
     * <p>Session tokens are single-use, so two contexts booting from one cached
     * token do not both work — see {@link ApiSessions} for why that fails as
     * intermittent flake rather than a clean error. A fresh HTTP login per method
     * costs a few hundred milliseconds and sidesteps the whole problem.
     */
    @Override
    protected String storageState() {
        if (!TestUsers.isConfigured()) {
            throw new SkipException("FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD not set — "
                + "seed a test account (server/seedTestUser.js) to run this test.");
        }
        return ApiSessions.forSeededUser(playwright());
    }

    @Test(description = "Submitting the required fields succeeds and the deal appears in My Submissions as pending")
    public void submittingRequiredFieldsSucceedsAndShowsAsPending() {
        String dealName = "UI Test Deal " + System.currentTimeMillis();

        goTo("/submit-deal");
        SubmitDealPage submitDeal = new SubmitDealPage(page);
        submitDeal.fillRequiredFields(dealName, "Electronics", 149.99);
        submitDeal.submit();

        assertThat(submitDeal.successBanner()).isVisible();
        submitDeal.viewMySubmissionsLink().click();

        MySubmissionsPage mySubmissions = new MySubmissionsPage(page);
        assertThat(mySubmissions.submissionCard(dealName)).isVisible();
        assertThat(mySubmissions.statusBadge(dealName)).containsText("Pending");
    }

    @Test(description = "Submitting without a category is blocked client-side")
    public void missingCategoryShowsValidationError() {
        goTo("/submit-deal");
        SubmitDealPage submitDeal = new SubmitDealPage(page);
        submitDeal.nameInput().fill("Deal Missing Category " + System.currentTimeMillis());
        submitDeal.currentPriceInput().fill("99.99");
        // Category left at its default "Select category" option on purpose.
        submitDeal.submit();

        assertThat(submitDeal.errorBanner()).isVisible();
        Assert.assertTrue(submitDeal.errorBanner().innerText().toLowerCase().contains("category"));
    }
}

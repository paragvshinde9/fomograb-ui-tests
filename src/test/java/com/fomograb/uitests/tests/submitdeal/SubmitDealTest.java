package com.fomograb.uitests.tests.submitdeal;

import com.fomograb.uitests.config.TestUsers;
import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.LoginPage;
import com.fomograb.uitests.pages.MySubmissionsPage;
import com.fomograb.uitests.pages.SubmitDealPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * SubmitDeal.tsx, gated behind login by App.tsx. Every run submits a
 * uniquely-named deal (timestamp suffix) rather than reusing a fixed name —
 * this hits the real, non-mocked {@code POST /community/submit} endpoint and
 * the deal stays in the database afterwards, so re-using one name across runs
 * would just accumulate confusing duplicates for whoever moderates submissions.
 */
@Test(groups = Tags.AUTH)
public class SubmitDealTest extends BaseTest {

    // TestNG always runs a superclass's @BeforeMethod (BaseTest#createContextAndPage,
    // which creates `page`) before a subclass's own @BeforeMethod — no explicit
    // ordering needed.
    @BeforeMethod(alwaysRun = true)
    public void requireSeededUserAndLogIn() {
        if (!TestUsers.isConfigured()) {
            throw new SkipException("FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD not set — "
                + "seed a test account (server/seedTestUser.js) to run this test.");
        }
        goTo("/login");
        LoginPage login = new LoginPage(page);
        login.fillCredentials(TestUsers.standardEmail(), TestUsers.standardPassword());
        login.submitExpectingSuccess();
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

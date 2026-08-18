package com.fomograb.uitests.tests.auth;

import com.fomograb.uitests.config.TestUsers;
import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.DashboardPage;
import com.fomograb.uitests.pages.LoginPage;
import com.fomograb.uitests.pages.MySubmissionsPage;
import com.fomograb.uitests.pages.SubmitDealPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The full session lifecycle in one continuous browser context: a guest gets
 * turned away from a gated page, logs in, uses the account to submit a deal,
 * confirms it landed, then logs out and confirms the session is really gone —
 * not just that the header changed, but that the gate closes again too.
 *
 * <p>Lives in the {@code auth} package (not {@code e2e}) purely so testng.xml
 * picks it up under the single-threaded "Auth" {@code <test>} block — it hits
 * the same rate-limited login endpoint as {@link LoginTest}. Its {@code groups}
 * still mark it as {@link Tags#E2E} so {@code -Dgroups=e2e} finds it too.
 */
@Test(groups = {Tags.AUTH, Tags.E2E})
public class LoginSubmitDealLogoutE2ETest extends BaseTest {

    @Test(description = "Guest is gated to /login, logs in, submits a deal, sees it pending, logs out, and is gated again")
    public void fullSessionLifecycle() {
        if (!TestUsers.isConfigured()) {
            throw new SkipException("FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD not set — "
                + "seed a test account (server/seedTestUser.js) to run this test.");
        }

        // 1. Guest hits a gated page and is bounced to /login (App.tsx's gated()).
        goTo("/submit-deal");
        page.waitForURL(Pattern.compile(".*/login"));

        // 2. Log in from right there.
        LoginPage login = new LoginPage(page);
        login.fillCredentials(TestUsers.standardEmail(), TestUsers.standardPassword());
        login.submitExpectingSuccess();

        // 3. Head to Submit Deal (now reachable) and submit one.
        String dealName = "E2E Journey Deal " + System.currentTimeMillis();
        goTo("/submit-deal");
        SubmitDealPage submitDeal = new SubmitDealPage(page);
        submitDeal.fillRequiredFields(dealName, "Electronics", 79.99);
        submitDeal.submit();
        assertThat(submitDeal.successBanner()).isVisible();

        // 4. Confirm it shows up pending in My Submissions.
        submitDeal.viewMySubmissionsLink().click();
        MySubmissionsPage mySubmissions = new MySubmissionsPage(page);
        assertThat(mySubmissions.submissionCard(dealName)).isVisible();
        assertThat(mySubmissions.statusBadge(dealName)).containsText("Pending");

        // 5. Log out from the homepage header.
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        Assert.assertTrue(dashboard.isLoggedIn(), "Should still be logged in after step 4");
        dashboard.logout();
        Assert.assertFalse(dashboard.isLoggedIn());

        // 6. The gate is back: submit-deal should bounce to /login again, proving
        //    logout actually cleared the session rather than just the header UI.
        goTo("/submit-deal");
        page.waitForURL(Pattern.compile(".*/login"));
    }
}

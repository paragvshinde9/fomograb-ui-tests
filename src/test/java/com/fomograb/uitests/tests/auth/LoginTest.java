package com.fomograb.uitests.tests.auth;

import com.fomograb.uitests.config.TestUsers;
import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.LoginPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Login.tsx. See testng.xml — this whole "auth" test package is deliberately
 * run single-threaded, because the backend rate-limits both login
 * and signup; running these concurrently with themselves would just produce
 * flaky 429s instead of real signal.
 */
@Test(groups = Tags.AUTH)
public class LoginTest extends BaseTest {

    @Test(description = "A seeded, valid account can log in and lands past /login")
    public void loginWithSeededUserSucceeds() {
        if (!TestUsers.isConfigured()) {
            throw new SkipException("FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD not set — "
                + "seed a test account (server/seedTestUser.js) to run this test.");
        }
        goTo("/login");
        LoginPage login = new LoginPage(page);
        login.fillCredentials(TestUsers.standardEmail(), TestUsers.standardPassword());
        login.submitExpectingSuccess();

        Assert.assertFalse(page.url().contains("/login"), "Should have navigated away from /login on success");
    }

    @Test(description = "An unknown email is rejected with the server's 'Invalid credentials' message")
    public void loginWithUnknownEmailShowsServerError() {
        goTo("/login");
        LoginPage login = new LoginPage(page);
        // A syntactically valid but essentially-guaranteed-not-to-exist address —
        // this test needs no seeded fixture, unlike the success-path test above.
        login.fillCredentials("no-such-user+ui-tests@fomograb-tests.invalid", "SomeWrongPassword123");

        String error = login.submitExpectingError();
        Assert.assertTrue(error.toLowerCase().contains("invalid"),
            "Expected an 'invalid credentials' style message, got: " + error);
    }

    @Test(description = "A whitespace-only email is caught by client-side validation before any request is sent")
    public void loginWithWhitespaceEmailShowsClientValidationError() {
        // Not an empty string: Login.tsx's email <input> is `required`, and native
        // HTML5 constraint validation (which fires on the real click, before React
        // ever sees a submit event) treats a genuinely empty field as invalid and
        // blocks submission with a browser tooltip we can't assert on. A few spaces
        // pass that native check but still fail Login.tsx's own `email.trim()` guard,
        // so this is the only way to reach the *client-side* "required" message.
        goTo("/login");
        LoginPage login = new LoginPage(page);
        login.emailInput().fill("   ");
        login.passwordInput().fill("SomePassword123");

        String error = login.submitExpectingError();
        Assert.assertTrue(error.toLowerCase().contains("email"), "Expected an email-related validation message, got: " + error);
    }
}

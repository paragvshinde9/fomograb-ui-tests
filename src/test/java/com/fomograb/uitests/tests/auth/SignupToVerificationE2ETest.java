package com.fomograb.uitests.tests.auth;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.SignupPage;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Intentionally disabled — kept in the suite (rather than deleted) so the gap
 * is visible and the test is ready to switch on the moment its one blocker is
 * fixed, instead of someone having to rediscover and rewrite it later.
 *
 * <p><b>Why it's off:</b> Signup.tsx's real success path hands off to
 * VerifyEmail.tsx, which needs a live 6-digit OTP from a real email. This test
 * can prove the handoff happens (fill the form, submit, see "Check your
 * email"), but doing that means firing a real, non-mocked
 * {@code POST /api/auth/signup} — creating an actual unverified account in the
 * database and sending a real email — on every single CI run, forever, with
 * no way to clean the account up (there's no delete-unverified-user endpoint)
 * or complete the flow (no OTP to read). That's a cost this suite shouldn't
 * pay just to prove one screen transition.
 *
 * <p><b>What would flip this on:</b> an email-capture sink in the target
 * environment (e.g. Mailpit/Mailhog fronting a dev SMTP relay) with an API to
 * pull the OTP out of the received message, or a backend test-mode endpoint
 * that returns/accepts a fixed code. Either way, wire it up in
 * {@link com.fomograb.uitests.config.TestConfig}, fetch the code here, and
 * feed the six digits into VerifyEmail's inputs.
 */
@Test(groups = {Tags.AUTH, Tags.E2E})
public class SignupToVerificationE2ETest extends BaseTest {

    @Test(enabled = false,
        description = "Signing up with valid, unique details hands off to the Check-your-email screen")
    public void signupHandsOffToEmailVerification() {
        goTo("/signup");
        SignupPage signup = new SignupPage(page);
        String uniqueEmail = "ui-tests+" + System.currentTimeMillis() + "@fomograb-tests.invalid";
        signup.fillForm("UI Test User", uniqueEmail, "ValidPass123", "ValidPass123");
        signup.submitButton().click();

        assertThat(page.locator(".verify-email-title")).hasText(Pattern.compile("Check your email", Pattern.CASE_INSENSITIVE));
    }
}

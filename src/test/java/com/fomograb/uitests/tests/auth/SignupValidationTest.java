package com.fomograb.uitests.tests.auth;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.SignupPage;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Signup.tsx's client-side {@code validateForm()} never reaches the network —
 * exercising it is fast and needs no seeded account. It still runs under the
 * {@code auth} group (single-threaded, see testng.xml) purely to keep it next
 * to {@link LoginTest} conceptually; these particular cases fire zero requests.
 *
 * <p>Every field here is HTML {@code required} (and email is {@code type="email"}),
 * so native browser constraint validation runs before React's handleSubmit ever
 * sees the click. Each case below supplies natively-valid values for every field
 * it isn't specifically testing, so the one deliberately-bad field is what
 * validateForm() actually catches.
 */
@Test(groups = Tags.AUTH)
public class SignupValidationTest extends BaseTest {

    @Test(description = "A one-character name is rejected as too short")
    public void nameShorterThanTwoCharsIsRejected() {
        goTo("/signup");
        SignupPage signup = new SignupPage(page);
        signup.fillForm("A", "valid@example.com", "ValidPass123", "ValidPass123");

        String error = signup.submitExpectingValidationError();
        Assert.assertTrue(error.toLowerCase().contains("name"), "Expected a name-length validation message, got: " + error);
    }

    @Test(description = "A password under 8 characters is rejected")
    public void passwordShorterThanEightCharsIsRejected() {
        goTo("/signup");
        SignupPage signup = new SignupPage(page);
        signup.fillForm("Valid Name", "valid@example.com", "Ab1", "Ab1");

        String error = signup.submitExpectingValidationError();
        Assert.assertTrue(error.toLowerCase().contains("password"), "Expected a password-length validation message, got: " + error);
    }

    @Test(description = "Password and confirm-password must match")
    public void mismatchedPasswordsAreRejected() {
        goTo("/signup");
        SignupPage signup = new SignupPage(page);
        signup.fillForm("Valid Name", "valid@example.com", "ValidPass123", "Different456");

        String error = signup.submitExpectingValidationError();
        Assert.assertTrue(error.toLowerCase().contains("match"), "Expected a passwords-do-not-match message, got: " + error);
    }
}

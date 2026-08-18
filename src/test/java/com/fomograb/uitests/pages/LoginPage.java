package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /login} — {@code src/components/Login.tsx} rendered inside AuthPage's
 * shell. Every interactive element here has either a real {@code <label for>}
 * or a unique {@code title} attribute, so no CSS/XPath fallbacks were needed —
 * {@code title} is used instead of the button text because the "Sign up" /
 * "Login" wording is duplicated by AuthPage's own tab buttons on this same
 * screen, which would make a text-based locator ambiguous.
 */
public class LoginPage extends BasePage {

    public LoginPage(Page page) {
        super(page);
    }

    public Locator emailInput() {
        return page.getByLabel("Email or Username");
    }

    public Locator passwordInput() {
        return page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator submitButton() {
        return page.getByTitle("Sign in to your account");
    }

    public Locator forgotPasswordLink() {
        return page.getByTitle("Reset your password");
    }

    public Locator switchToSignupLink() {
        return page.getByTitle("Create a new account");
    }

    public Locator errorMessage() {
        return page.locator(".auth-content .error-message");
    }

    public LoginPage fillCredentials(String email, String password) {
        emailInput().fill(email);
        passwordInput().fill(password);
        return this;
    }

    /** Submits and waits for either navigation away from /login or an inline error to render. */
    public void submitExpectingSuccess() {
        submitButton().click();
        page.waitForURL(url -> !url.contains("/login"));
    }

    public String submitExpectingError() {
        submitButton().click();
        errorMessage().waitFor();
        return errorMessage().innerText();
    }

    public SignupPage switchToSignup() {
        switchToSignupLink().click();
        return new SignupPage(page);
    }
}

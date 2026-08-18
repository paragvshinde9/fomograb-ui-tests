package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /signup} — {@code src/components/Signup.tsx}. See {@link LoginPage}
 * for why {@code title} attributes are preferred over button text here.
 */
public class SignupPage extends BasePage {

    public SignupPage(Page page) {
        super(page);
    }

    public Locator nameInput() {
        return page.getByLabel("Full Name");
    }

    public Locator emailInput() {
        return page.getByLabel("Email", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator passwordInput() {
        return page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator confirmPasswordInput() {
        return page.getByLabel("Confirm Password");
    }

    public Locator submitButton() {
        return page.getByTitle("Create your account");
    }

    public Locator switchToLoginLink() {
        return page.getByTitle("Go to login form");
    }

    public Locator errorMessage() {
        return page.locator(".auth-content .error-message");
    }

    public SignupPage fillForm(String name, String email, String password, String confirmPassword) {
        nameInput().fill(name);
        emailInput().fill(email);
        passwordInput().fill(password);
        confirmPasswordInput().fill(confirmPassword);
        return this;
    }

    /** Client-side validation (empty fields, password mismatch, etc.) never leaves this page. */
    public String submitExpectingValidationError() {
        submitButton().click();
        errorMessage().waitFor();
        return errorMessage().innerText();
    }

    public LoginPage switchToLogin() {
        switchToLoginLink().click();
        return new LoginPage(page);
    }
}

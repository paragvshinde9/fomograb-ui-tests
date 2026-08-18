package com.fomograb.uitests.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * {@code /submit-deal} — {@code src/components/SubmitDeal.tsx}, auth-gated
 * (anonymous visitors are redirected to {@code /login} by App.tsx's
 * {@code gated()} helper before this page ever renders). Every field has a
 * real {@code <label for>}, so {@code getByLabel} is used throughout —
 * Playwright's default substring match means the label's trailing
 * {@code " *"} required-marker or {@code " ($)"} suffix doesn't need to be typed.
 */
public class SubmitDealPage extends BasePage {

    public SubmitDealPage(Page page) {
        super(page);
    }

    public Locator nameInput() {
        return page.getByLabel("Product Name");
    }

    public Locator brandInput() {
        return page.getByLabel("Brand");
    }

    public Locator categorySelect() {
        return page.getByLabel("Category", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator subcategorySelect() {
        return page.getByLabel("Subcategory");
    }

    public Locator currentPriceInput() {
        return page.getByLabel("Current Deal Price");
    }

    public Locator originalPriceInput() {
        return page.getByLabel("Original Price");
    }

    public Locator dealUrlInput() {
        return page.getByLabel("Deal URL");
    }

    public Locator descriptionInput() {
        return page.getByLabel("Description");
    }

    public Locator submitButton() {
        return page.locator(".submit-deal-btn");
    }

    public Locator errorBanner() {
        return page.locator(".submit-deal-error");
    }

    public Locator successBanner() {
        return page.locator(".submit-deal-success");
    }

    public Locator viewMySubmissionsLink() {
        return page.locator(".view-submissions-btn");
    }

    /** Fills only the required fields: name, category, and current price. */
    public SubmitDealPage fillRequiredFields(String name, String category, double currentPrice) {
        nameInput().fill(name);
        categorySelect().selectOption(category);
        currentPriceInput().fill(String.valueOf(currentPrice));
        return this;
    }

    public void submit() {
        submitButton().click();
    }
}

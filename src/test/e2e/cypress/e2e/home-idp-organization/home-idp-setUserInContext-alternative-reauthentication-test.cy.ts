import { testRealmLoginUri } from "../../fixtures/uri";
import { unverifiedIdpUser } from "../../fixtures/users";

describe('Reauthenticating a locally-authenticated session when setUserInContext is off (alternative topology)', () => {
    it('keeps the username locked and lets the user log in with just the password', () => {
        cy.visit(testRealmLoginUri);
        cy.screenshot('01-initial-visit');
        cy.get('#username').type(unverifiedIdpUser.username);
        cy.get('#kc-login').click();
        cy.screenshot('02-after-first-submit');
        cy.get('#password').type(unverifiedIdpUser.password);
        cy.get('#kc-login').click();
        cy.screenshot('03-after-second-submit');
        cy.contains('Personal');

        cy.visit(testRealmLoginUri.concat('&prompt=login'));
        cy.screenshot('04-after-forced-reauth-visit');
        cy.get('#username').should('not.exist');
        cy.get('#kc-login').click();
        cy.screenshot('05-after-first-submit-without-username');

        cy.get('#username').should('not.exist');
        cy.get('#password').type(unverifiedIdpUser.password);
        cy.get('#kc-login').click();
        cy.screenshot('06-after-second-submit');

        cy.contains('Personal');
    })
});

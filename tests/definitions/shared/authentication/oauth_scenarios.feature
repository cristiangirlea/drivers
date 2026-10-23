@core
Feature: OAuth scenarios

  Core wiremock scenarios for OAuth login.

  @core_int
  Scenario: should reject a mismatched username for a pre-acquired token
    Given Authentication is set to a pre-acquired token with a username that is not the user tied to the access token
    When Trying to Connect
    Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."

  @core_int
  Scenario: should reject a mismatched username for authorization code
    Given Authentication is set to authorization code with a username that is not the user tied to the access token
    When Trying to Connect
    Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."

  @core_int
  Scenario: should reject a mismatched username for client credentials
    Given Authentication is set to client credentials with a username that is not the user tied to the access token
    When Trying to Connect
    Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."

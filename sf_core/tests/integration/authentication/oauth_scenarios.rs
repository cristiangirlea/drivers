use super::oauth::{OAuthTestFixture, count_token_endpoint_requests, unique_user};
use crate::common::mocks::oauth;
use crate::common::tls_proxy::MockServerWithTls;

fn count_login_requests(mock: &MockServerWithTls) -> usize {
    mock.received_requests()
        .iter()
        .filter(|r| r.url.path() == "/session/v1/login-request" && r.method.as_str() == "POST")
        .count()
}

fn assert_username_mismatch_leaves_connection_down(
    fixture: &OAuthTestFixture,
    result: Result<(), String>,
) {
    let error = result.expect_err("username mismatch must fail connect");
    assert!(error.contains("390309"), "expected 390309, got: {error}");
    assert!(
        error.contains("differs from the user tied to the access token"),
        "expected the GS mismatch message, got: {error}"
    );
    assert_eq!(
        count_login_requests(&fixture.mock),
        1,
        "390309 must not be retried"
    );
    assert_eq!(
        count_token_endpoint_requests(&fixture.mock),
        0,
        "390309 must not refresh"
    );
    let query = fixture
        .client
        .execute_query_no_unwrap("SELECT 1")
        .expect_err("query must fail when login never completed");
    assert!(
        query.contains("not initialized"),
        "expected an uninitialized connection, got: {query}"
    );
}

#[test]
fn should_reject_a_mismatched_username_for_a_pre_acquired_token() {
    // Given Authentication is set to a pre-acquired token with a username that is not the user tied to the access token
    let user = unique_user("oauth_legacy_390309");
    let fixture = OAuthTestFixture::with_legacy_oauth(&user, "legacy-mismatched-user-token");
    fixture
        .mock
        .mount(oauth::snowflake_login_oauth_username_mismatch_390309());

    // When Trying to Connect
    let result = fixture.connect();

    // Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."
    assert_username_mismatch_leaves_connection_down(&fixture, result);
}

#[test]
fn should_reject_a_mismatched_username_for_authorization_code() {
    // Given Authentication is set to authorization code with a username that is not the user tied to the access token
    let user = unique_user("oauth_ac_390309");
    let fixture = OAuthTestFixture::with_authorization_code(&user);
    fixture.seed_access_token("ac-mismatched-user-access-token");
    fixture
        .mock
        .mount(oauth::snowflake_login_oauth_username_mismatch_390309());

    // When Trying to Connect
    let result = fixture.connect();

    // Then Connection fails with "The user you were trying to authenticate as differs from the user tied to the access token."
    assert_username_mismatch_leaves_connection_down(&fixture, result);
}

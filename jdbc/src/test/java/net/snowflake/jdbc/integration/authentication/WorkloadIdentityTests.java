package net.snowflake.jdbc.integration.authentication;

import static net.snowflake.jdbc.utils.DriverCompatibility.isNewDriver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import net.snowflake.client.api.driver.SnowflakeDriver;
import org.junit.jupiter.api.Test;

class WorkloadIdentityTests {

  private static final String JDBC_URL = "jdbc:snowflake://wif-test.snowflakecomputing.com";

  private static final String LEGACY_WIF_SQL_STATE = "08000";

  private static final int LEGACY_WIF_VENDOR_CODE = 200071;

  @Test
  void shouldFailWorkloadIdentityWhenProviderIsMissing() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY but WORKLOAD_IDENTITY_PROVIDER is absent
    Properties props = baseProperties();

    // When Trying to Connect
    SQLException exception = assertThrows(SQLException.class, () -> connect(props));

    // Then Connection fails with a missing-parameter error citing workload_identity_provider
    if (isNewDriver()) {
      assertMessageContains(exception, "missing required parameter", "workload_identity_provider");
      assertDiagnostics(exception, null, 0);
    } else {
      assertMessageContains(exception, "Unknown Workload Identity provider specified: null");
      assertLegacyWifFlowError(exception);
    }
  }

  @Test
  void shouldFailWorkloadIdentityWhenProviderIsAnInvalidValue() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is an
    // invalid value
    Properties props = baseProperties();
    props.setProperty("workloadIdentityProvider", "INVALID_CLOUD");

    // When Trying to Connect
    SQLException exception = assertThrows(SQLException.class, () -> connect(props));

    // Then Connection fails with an invalid-parameter error citing workload_identity_provider
    if (isNewDriver()) {
      assertMessageContains(exception, "workload_identity_provider", "invalid_cloud");
      assertDiagnostics(exception, null, 0);
    } else {
      assertMessageContains(
          exception, "Unknown Workload Identity provider specified: INVALID_CLOUD");
      assertLegacyWifFlowError(exception);
    }
  }

  @Test
  void shouldFailAwsWifWhenExternalIdIsSet() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY, WORKLOAD_IDENTITY_PROVIDER is AWS, and
    // workloadIdentityAwsExternalId is non-empty
    if (!isNewDriver()) {
      assumeTrue(
          !externalIdFeatureEnabled(),
          "SF_ENABLE_WIF_AWS_EXTERNAL_ID is set; snowflake-jdbc sends ExternalId in that case");
    }
    Properties props = baseProperties();
    props.setProperty("workloadIdentityProvider", "AWS");
    props.setProperty("workloadIdentityAwsExternalId", "external-id");

    // When Trying to Connect
    SQLException exception = assertThrows(SQLException.class, () -> connect(props));

    // Then Connection fails because the external id is not enabled
    if (isNewDriver()) {
      assertMessageContains(exception, "workloadIdentityAwsExternalId", "not enabled");
      assertDiagnostics(exception, null, 0);
    } else {
      assertMessageContains(
          exception, "Connection property workloadIdentityAwsExternalId is not enabled");
      assertLegacyWifFlowError(exception);
    }
  }

  @Test
  void shouldFailOidcWifWhenTokenIsMissing() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is OIDC but
    // token is absent
    Properties props = baseProperties();
    props.setProperty("workloadIdentityProvider", "OIDC");

    // When Trying to Connect
    SQLException exception = assertThrows(SQLException.class, () -> connect(props));

    // Then Connection fails with a missing-parameter error citing token
    if (isNewDriver()) {
      assertMessageContains(exception, "missing required parameter", "token");
      assertDiagnostics(exception, null, 0);
    } else {
      assertMessageContains(exception, "No OIDC token was specified");
      assertLegacyWifFlowError(exception);
    }
  }

  @Test
  void shouldFailOidcWifWhenTokenIsMalformed() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is OIDC
    Properties props = baseProperties();
    props.setProperty("workloadIdentityProvider", "OIDC");
    // And Token is set to a malformed value that is not a valid JWT
    props.setProperty("token", "not-a-valid-jwt");

    // When Trying to Connect
    SQLException exception = assertThrows(SQLException.class, () -> connect(props));

    // Then Connection fails with an attestation error indicating a malformed token
    if (isNewDriver()) {
      String message = exception.getMessage().toLowerCase();
      assertTrue(
          message.contains("not a valid jwt") || message.contains("malformed"),
          () -> "Expected a malformed JWT error, but got: " + exception.getMessage());
      assertDiagnostics(exception, null, 0);
    } else {
      assertMessageContains(exception, "Failed to parse JWT and extract claims");
      assertLegacyWifFlowError(exception);
    }
  }

  private static void assertDiagnostics(SQLException exception, String sqlState, int vendorCode) {
    assertEquals(sqlState, exception.getSQLState(), "SQLSTATE");
    assertEquals(vendorCode, exception.getErrorCode(), "vendor code");
  }

  private static void assertLegacyWifFlowError(SQLException exception) {
    assertDiagnostics(exception, LEGACY_WIF_SQL_STATE, LEGACY_WIF_VENDOR_CODE);
  }

  private static void assertMessageContains(SQLException exception, String... fragments) {
    String message = exception.getMessage().toLowerCase();
    for (String fragment : fragments) {
      assertTrue(
          message.contains(fragment.toLowerCase()),
          () -> "Expected \"" + fragment + "\" in: " + exception.getMessage());
    }
  }

  private static boolean externalIdFeatureEnabled() {
    String value = System.getenv("SF_ENABLE_WIF_AWS_EXTERNAL_ID");
    return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private static Properties baseProperties() {
    Properties props = new Properties();
    props.setProperty("account", "wiftest");
    props.setProperty("authenticator", "WORKLOAD_IDENTITY");
    return props;
  }

  private static void connect(Properties props) throws Exception {
    Class.forName(SnowflakeDriver.class.getName());
    try (Connection ignored = DriverManager.getConnection(JDBC_URL, props)) {}
  }
}

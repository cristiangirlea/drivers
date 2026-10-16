package net.snowflake.jdbc.e2e.authentication;

import static net.snowflake.jdbc.utils.TestParameters.get;
import static net.snowflake.jdbc.utils.TestParameters.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.snowflake.client.api.driver.SnowflakeDriver;
import net.snowflake.jdbc.utils.WithConnect;
import net.snowflake.jdbc.utils.WithQueryUtils;
import org.junit.jupiter.api.Test;

class WorkloadIdentityTests implements WithQueryUtils, WithConnect {

  @Test
  void shouldAuthenticateWithCloudIdentityUsingWif() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is configured
    String provider = requireProvider();
    Properties props = wifProperties(provider);

    // When Trying to Connect
    try (Connection conn = connect(props)) {
      // Then Login is successful and a simple query can be executed
      assertSimpleQuerySucceeds(conn);
    }
  }

  @Test
  void shouldAuthenticateWithWifUsingLowercaseAuthenticator() throws Exception {
    // Given Authentication is set to workload_identity (lowercase) and a valid provider is
    // configured
    String provider = requireProvider();
    Properties props = wifProperties(provider);
    props.setProperty("authenticator", "workload_identity");

    // When Trying to Connect
    try (Connection conn = connect(props)) {
      // Then Login is successful and a simple query can be executed
      assertSimpleQuerySucceeds(conn);
    }
  }

  @Test
  void shouldAuthenticateWithWifUsingImpersonation() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_IMPERSONATION_PATH is
    // configured
    String provider = requireProvider();
    assumeTrue(
        has("SNOWFLAKE_TEST_WIF_IMPERSONATION_PATH")
            && !get("SNOWFLAKE_TEST_WIF_IMPERSONATION_PATH").isEmpty(),
        "SNOWFLAKE_TEST_WIF_IMPERSONATION_PATH not set");
    Properties props = wifProperties(provider);
    props.setProperty(
        "workloadIdentityImpersonationPath", get("SNOWFLAKE_TEST_WIF_IMPERSONATION_PATH"));

    // When Trying to Connect
    try (Connection conn = connect(props)) {
      // Then Login is successful and a simple query can be executed
      assertSimpleQuerySucceeds(conn);
    }
  }

  @Test
  void shouldAuthenticateAwsWifUsingPreSignedGetCallerIdentityByDefault() throws Exception {
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is AWS
    assumeTrue(isAwsProvider(), "SNOWFLAKE_TEST_WIF_PROVIDER is not AWS");

    // And SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN is not set
    try (AwsWorker worker = new AwsWorker()) {
      // When Trying to Connect
      Process process = worker.start();

      // Then Login is successful and a simple query can be executed
      assertEquals(0, process.exitValue(), "Worker JVM failed:\n" + worker.log());
    }
  }

  @Test
  // spotless:off
  void shouldAuthenticateAwsWifUsingGetWebIdentityTokenWhenWorkloadIdentityAwsUseOutboundTokenIsTrue()
      throws Exception {
    // spotless:on
    // Given Authentication is set to WORKLOAD_IDENTITY and WORKLOAD_IDENTITY_PROVIDER is AWS
    assumeTrue(isAwsProvider(), "SNOWFLAKE_TEST_WIF_PROVIDER is not AWS");

    // And SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN is not set
    try (AwsWorker worker = new AwsWorker()) {
      // And workload_identity_aws_use_outbound_token is set to true
      worker.useOutboundToken();

      // When Trying to Connect
      Process process = worker.start();

      // Then Login is successful and a simple query can be executed
      assertEquals(0, process.exitValue(), "Worker JVM failed:\n" + worker.log());
    }
  }

  private static String requireProvider() {
    assumeTrue(
        has("SNOWFLAKE_TEST_WIF_PROVIDER") && !get("SNOWFLAKE_TEST_WIF_PROVIDER").isEmpty(),
        "SNOWFLAKE_TEST_WIF_PROVIDER not set");
    return get("SNOWFLAKE_TEST_WIF_PROVIDER");
  }

  private static boolean isAwsProvider() {
    return has("SNOWFLAKE_TEST_WIF_PROVIDER")
        && get("SNOWFLAKE_TEST_WIF_PROVIDER").equalsIgnoreCase("AWS");
  }

  /**
   * sf_core reads {@code SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN} from the process environment. The
   * connection runs in a child JVM with that variable removed, so a parent that has it set still
   * exercises the requested attestation path.
   */
  private static final class AwsWorker implements AutoCloseable {
    private final Path log;
    private final List<String> command;
    private final ProcessBuilder processBuilder;
    private boolean outbound;
    private Process process;

    private AwsWorker() throws Exception {
      log = Files.createTempFile("wif-aws-", ".log");
      command = workerCommand();
      processBuilder = new ProcessBuilder(command);
      processBuilder.environment().remove("SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN");
      processBuilder.redirectErrorStream(true);
      processBuilder.redirectOutput(log.toFile());
    }

    private void useOutboundToken() {
      outbound = true;
    }

    private Process start() throws Exception {
      command.add(outbound ? "outbound" : "default");
      process = processBuilder.start();
      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
        fail("Worker JVM timed out:\n" + log());
      }
      return process;
    }

    private String log() throws Exception {
      return readWorkerLog(log);
    }

    @Override
    public void close() throws Exception {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
      Files.deleteIfExists(log);
    }
  }

  /** Worker entry point. Argument is {@code default} or {@code outbound}. */
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || (!"default".equals(args[0]) && !"outbound".equals(args[0]))) {
      System.err.println("Usage: WorkloadIdentityTests <default|outbound>");
      System.exit(2);
    }
    if (System.getenv("SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN") != null) {
      System.err.println("SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN is still set");
      System.exit(2);
    }

    Properties props = wifProperties("AWS");
    if ("outbound".equals(args[0])) {
      props.setProperty("workloadIdentityAwsUseOutboundToken", "true");
    }
    Class.forName(SnowflakeDriver.class.getName());
    WorkloadIdentityTests test = new WorkloadIdentityTests();
    try (Connection conn = test.connect(props)) {
      test.assertSimpleQuerySucceeds(conn);
    }
  }

  private static List<String> workerCommand() {
    List<String> cmd = new ArrayList<>();
    cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (arg.startsWith("--add-opens") || arg.startsWith("--enable-native-access")) {
        cmd.add(arg);
      }
    }
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));
    cmd.add(WorkloadIdentityTests.class.getName());
    return cmd;
  }

  private static String readWorkerLog(Path workerLog) throws Exception {
    if (!Files.exists(workerLog)) {
      return "";
    }
    return new String(Files.readAllBytes(workerLog), StandardCharsets.UTF_8);
  }

  private static Properties wifProperties(String provider) {
    Properties props = new Properties();
    if (has("SNOWFLAKE_TEST_WIF_HOST") && !get("SNOWFLAKE_TEST_WIF_HOST").isEmpty()) {
      props.setProperty("host", get("SNOWFLAKE_TEST_WIF_HOST"));
    } else {
      props.setProperty("host", get("SNOWFLAKE_TEST_HOST"));
    }
    if (has("SNOWFLAKE_TEST_WIF_ACCOUNT") && !get("SNOWFLAKE_TEST_WIF_ACCOUNT").isEmpty()) {
      props.setProperty("account", get("SNOWFLAKE_TEST_WIF_ACCOUNT"));
    } else {
      props.setProperty("account", get("SNOWFLAKE_TEST_ACCOUNT"));
    }
    if (has("SNOWFLAKE_TEST_WIF_USER") && !get("SNOWFLAKE_TEST_WIF_USER").isEmpty()) {
      props.setProperty("user", get("SNOWFLAKE_TEST_WIF_USER"));
    }
    if (has("SNOWFLAKE_TEST_WIF_ENTRA_RESOURCE")
        && !get("SNOWFLAKE_TEST_WIF_ENTRA_RESOURCE").isEmpty()) {
      props.setProperty("workloadIdentityEntraResource", get("SNOWFLAKE_TEST_WIF_ENTRA_RESOURCE"));
    }
    props.setProperty("authenticator", "WORKLOAD_IDENTITY");
    props.setProperty("workloadIdentityProvider", provider);
    return props;
  }
}

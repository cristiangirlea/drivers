package net.snowflake.jdbc.e2e.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import net.snowflake.client.api.connection.SnowflakeConnection;
import net.snowflake.jdbc.utils.PoolingTestCompat;
import net.snowflake.jdbc.utils.SnowflakeIntegrationTestBase;
import org.junit.jupiter.api.Test;

class SessionContextTests extends SnowflakeIntegrationTestBase {

  @Test
  void shouldReturnTheCurrentSessionRole() throws Exception {
    // Given Snowflake client is logged in
    try (Connection conn = openConnection();
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT CURRENT_ROLE()")) {
      // When the current role is read from the session and from CURRENT_ROLE()
      assertTrue(resultSet.next(), "Expected one row");
      String fromQuery = resultSet.getString(1);
      assertMatchesNullability(resultSet, fromQuery);
      assertFalse(resultSet.next(), "Expected exactly one row");

      // Then both report the same role, including when none is set
      assertEquals(fromQuery, conn.unwrap(SnowflakeConnection.class).getRole());
    }
  }

  @Test
  void shouldReturnTheCurrentSessionWarehouse() throws Exception {
    // Given Snowflake client is logged in
    try (Connection conn = openConnection();
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT CURRENT_WAREHOUSE()")) {
      // When the current warehouse is read from the session and from CURRENT_WAREHOUSE()
      assertTrue(resultSet.next(), "Expected one row");
      String fromQuery = resultSet.getString(1);
      assertMatchesNullability(resultSet, fromQuery);
      assertFalse(resultSet.next(), "Expected exactly one row");

      // Then both report the same warehouse, including when none is set
      assertEquals(fromQuery, conn.unwrap(SnowflakeConnection.class).getWarehouse());
    }
  }

  @Test
  void shouldReturnTheCurrentSessionDatabase() throws Exception {
    // Given Snowflake client is logged in
    try (Connection conn = openConnection()) {
      ensureDatabaseAndSchema(conn);
      try (Statement statement = conn.createStatement();
          ResultSet resultSet = statement.executeQuery("SELECT CURRENT_DATABASE()")) {
        // When the current database is read from the session and from CURRENT_DATABASE()
        assertTrue(resultSet.next(), "Expected one row");
        String fromQuery = resultSet.getString(1);
        assertMatchesNullability(resultSet, fromQuery);
        assertFalse(resultSet.next(), "Expected exactly one row");

        // Then the session database matches the query and Connection.getCatalog()
        SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
        assertEquals(fromQuery, session.getDatabase());
        assertEquals(conn.getCatalog(), session.getDatabase());
      }
    }
  }

  @Test
  void shouldUpdateSessionRoleAfterUseRole() throws Exception {
    // Given Snowflake client is logged in with a non-PUBLIC role
    try (Connection conn = openConnection()) {
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      String originalRole = session.getRole();
      assumeTrue(originalRole != null && !originalRole.isEmpty());
      assumeTrue(
          !"PUBLIC".equalsIgnoreCase(originalRole),
          "Starting role must not be PUBLIC so USE ROLE PUBLIC changes the session");

      // When USE ROLE PUBLIC is executed
      executeUseIdentifier(conn, "USE ROLE", "PUBLIC");
      // Then getRole reports PUBLIC
      assertEquals("PUBLIC", session.getRole());

      // When USE ROLE restores the original role
      executeUseIdentifier(conn, "USE ROLE", originalRole);
      // Then getRole reports the original role
      assertEquals(originalRole, session.getRole());
    }
  }

  @Test
  void shouldUpdateSessionWarehouseAfterUseWarehouse() throws Exception {
    // Given Snowflake client is logged in and another usable warehouse exists
    try (Connection conn = openConnection();
        Statement statement = conn.createStatement()) {
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      String originalWarehouse = session.getWarehouse();
      assumeTrue(originalWarehouse != null && !originalWarehouse.isEmpty());
      String alternateWarehouse = findAlternateWarehouse(statement, originalWarehouse);
      assumeTrue(
          alternateWarehouse != null,
          "The test account must grant a warehouse other than " + originalWarehouse);

      // When USE WAREHOUSE switches to the alternate warehouse
      executeUseIdentifier(conn, "USE WAREHOUSE", alternateWarehouse);
      // Then getWarehouse reports the alternate warehouse
      assertEquals(alternateWarehouse, session.getWarehouse());

      // When USE WAREHOUSE restores the original warehouse
      executeUseIdentifier(conn, "USE WAREHOUSE", originalWarehouse);
      // Then getWarehouse reports the original warehouse
      assertEquals(originalWarehouse, session.getWarehouse());
    }
  }

  @Test
  void shouldUpdateSessionDatabaseAfterUseDatabaseAndSetCatalog() throws Exception {
    // Given Snowflake client is logged in and a temporary database is created
    String temporaryDatabase =
        ("JDBC_SESSION_CTX_" + UUID.randomUUID().toString().replace("-", "")).toUpperCase();
    try (Connection conn = openConnection();
        Statement statement = conn.createStatement()) {
      ensureDatabaseAndSchema(conn);
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      String originalDatabase = session.getDatabase();
      assumeTrue(originalDatabase != null && !originalDatabase.isEmpty());
      statement.execute("CREATE DATABASE " + temporaryDatabase);
      try {
        // When USE DATABASE switches to the temporary database
        executeUseIdentifier(conn, "USE DATABASE", temporaryDatabase);
        // Then getDatabase and getCatalog report the temporary database
        assertEquals(temporaryDatabase, session.getDatabase());
        assertEquals(temporaryDatabase, conn.getCatalog());

        // When setCatalog restores the original database
        conn.setCatalog(originalDatabase);
        // Then getDatabase and getCatalog report the original database
        assertEquals(originalDatabase, session.getDatabase());
        assertEquals(originalDatabase, conn.getCatalog());
      } finally {
        executeUseIdentifier(conn, "USE DATABASE", originalDatabase);
        statement.execute("DROP DATABASE IF EXISTS " + temporaryDatabase);
      }
    }
  }

  @Test
  void shouldThrowWhenSessionRoleIsReadOnAClosedConnection() throws Exception {
    // Given Snowflake client is logged in and the connection is closed
    try (Connection conn = openConnection()) {
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      conn.close();
      // When the session role is read
      // Then a connection closed error is raised
      PoolingTestCompat.assertConnectionClosed(assertThrows(SQLException.class, session::getRole));
    }
  }

  @Test
  void shouldThrowWhenSessionWarehouseIsReadOnAClosedConnection() throws Exception {
    // Given Snowflake client is logged in and the connection is closed
    try (Connection conn = openConnection()) {
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      conn.close();
      // When the session warehouse is read
      // Then a connection closed error is raised
      PoolingTestCompat.assertConnectionClosed(
          assertThrows(SQLException.class, session::getWarehouse));
    }
  }

  @Test
  void shouldThrowWhenSessionDatabaseIsReadOnAClosedConnection() throws Exception {
    // Given Snowflake client is logged in and the connection is closed
    try (Connection conn = openConnection()) {
      SnowflakeConnection session = conn.unwrap(SnowflakeConnection.class);
      conn.close();
      // When the session database is read
      // Then a connection closed error is raised
      PoolingTestCompat.assertConnectionClosed(
          assertThrows(SQLException.class, session::getDatabase));
    }
  }

  private static void assertMatchesNullability(ResultSet resultSet, String value)
      throws SQLException {
    if (value == null) {
      assertTrue(resultSet.wasNull());
    } else {
      assertFalse(resultSet.wasNull());
    }
  }

  private static String findAlternateWarehouse(Statement statement, String originalWarehouse)
      throws SQLException {
    try (ResultSet resultSet = statement.executeQuery("SHOW WAREHOUSES")) {
      while (resultSet.next()) {
        String name = resultSet.getString("name");
        if (name != null && !name.equalsIgnoreCase(originalWarehouse)) {
          return name;
        }
      }
    }
    return null;
  }

  private static void executeUseIdentifier(Connection conn, String useCommand, String identifier)
      throws SQLException {
    try (PreparedStatement statement = conn.prepareStatement(useCommand + " IDENTIFIER(?)")) {
      statement.setString(1, identifier);
      statement.execute();
    }
  }
}

package net.snowflake.client.internal.api.implementation.exception;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import lombok.Getter;

/**
 * Carrier for a batch failure, mapping to a checked {@link BatchUpdateException} and preserving the
 * exact {@code (message, sqlState, vendorCode, updateCounts, cause)} tuple. Thrown by impl-tier
 * batch code ({@code StatementBatch}, {@code PreparedBatch}).
 *
 * <p>The {@code long[]} constructor keeps counts above {@code Integer.MAX_VALUE} available through
 * {@link BatchUpdateException#getLargeUpdateCounts()}. The {@code int[]} constructor is the packed
 * failure path used by prepared batches.
 */
@Getter
public class SFBatchUpdateException extends DriverRuntimeException {
  private static final long serialVersionUID = 1L;

  private final String sqlState;
  private final int vendorCode;
  private final int[] updateCounts;
  private final long[] largeUpdateCounts;

  public SFBatchUpdateException(
      String message, String sqlState, int vendorCode, int[] updateCounts, Throwable cause) {
    super(message, cause);
    this.sqlState = sqlState;
    this.vendorCode = vendorCode;
    this.updateCounts = updateCounts;
    this.largeUpdateCounts = null;
  }

  public SFBatchUpdateException(
      String message, String sqlState, int vendorCode, long[] largeUpdateCounts, Throwable cause) {
    super(message, cause);
    this.sqlState = sqlState;
    this.vendorCode = vendorCode;
    this.updateCounts = null;
    this.largeUpdateCounts = largeUpdateCounts;
  }

  @Override
  public SQLException toSQLException() {
    if (largeUpdateCounts != null) {
      return new BatchUpdateException(
          getMessage(), sqlState, vendorCode, largeUpdateCounts, getCause());
    }
    return new BatchUpdateException(getMessage(), sqlState, vendorCode, updateCounts, getCause());
  }
}

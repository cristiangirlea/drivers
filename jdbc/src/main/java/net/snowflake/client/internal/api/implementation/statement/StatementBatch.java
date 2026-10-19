package net.snowflake.client.internal.api.implementation.statement;

import static net.snowflake.client.internal.api.implementation.statement.StatementTypeClassifier.NO_UPDATE_COUNT;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import net.snowflake.client.internal.api.implementation.exception.CoreException;
import net.snowflake.client.internal.api.implementation.exception.SFBatchUpdateException;

/**
 * Per-statement batch of SQL strings. {@link #executeAll} / {@link #executeAllAsInt} run each entry
 * sequentially per JDBC §14 (continue-on-error) and surface the first failure as a {@link
 * SFBatchUpdateException}.
 */
final class StatementBatch {
  private final List<String> entries = new ArrayList<>();

  void add(String sql) {
    entries.add(sql);
  }

  void clear() {
    entries.clear();
  }

  int size() {
    return entries.size();
  }

  /**
   * Runs every queued statement and returns one int per entry. Overflow and {@code NO_UPDATE_COUNT}
   * collapse via {@link SnowflakeStatementImpl#toBatchInt(long)}. Failure exceptions carry the
   * narrowed {@code int[]} so {@link java.sql.BatchUpdateException#getUpdateCounts()} stays
   * consistent with {@link Statement#executeBatch()}.
   */
  int[] executeAllAsInt(SnowflakeStatementImpl stmt) {
    long[] updateCounts = runAll(stmt, false);
    return toIntUpdateCounts(updateCounts);
  }

  /**
   * Runs every queued statement and returns one long per entry. {@code NO_UPDATE_COUNT} becomes
   * {@link Statement#SUCCESS_NO_INFO}; failed entries are {@link Statement#EXECUTE_FAILED}. Counts
   * above {@link Integer#MAX_VALUE} are preserved. Failure exceptions carry the {@code long[]} so
   * {@link java.sql.BatchUpdateException#getLargeUpdateCounts()} keeps full fidelity.
   */
  long[] executeAll(SnowflakeStatementImpl stmt) {
    return runAll(stmt, true);
  }

  private long[] runAll(SnowflakeStatementImpl stmt, boolean preserveLargeCountsInFailure) {
    long[] updateCounts = new long[entries.size()];
    stmt.clearBatchQueryIds();
    SFBatchUpdateException pending = null;
    try {
      CoreException firstFailure = runEntries(stmt, updateCounts);
      if (firstFailure != null) {
        pending =
            preserveLargeCountsInFailure
                ? SnowflakeStatementImpl.buildBatchFailureException(firstFailure, updateCounts)
                : SnowflakeStatementImpl.buildBatchFailureException(
                    firstFailure, toIntUpdateCounts(updateCounts));
      }
    } finally {
      stmt.finalizeBatch(pending);
    }
    if (pending != null) {
      throw pending;
    }
    return updateCounts;
  }

  /** Returns the first per-row failure, or {@code null} if every entry succeeded. */
  private CoreException runEntries(SnowflakeStatementImpl stmt, long[] updateCounts) {
    CoreException firstFailure = null;
    for (int i = 0; i < entries.size(); i++) {
      CoreException rowFailure = runOne(stmt, entries.get(i), updateCounts, i);
      if (rowFailure != null && firstFailure == null) {
        firstFailure = rowFailure;
      }
    }
    return firstFailure;
  }

  /** Returns {@code null} on success, or the failure exception on per-row error. */
  private static CoreException runOne(
      SnowflakeStatementImpl stmt, String sql, long[] updateCounts, int row) {
    try {
      long count = stmt.executeLargeUpdateWithBindings(sql, null);
      updateCounts[row] = count == NO_UPDATE_COUNT ? Statement.SUCCESS_NO_INFO : count;
      return null;
    } catch (CoreException e) {
      updateCounts[row] = Statement.EXECUTE_FAILED;
      return e;
    } finally {
      // Always append, even on failure (queryId is null when applySingleResult never ran), so
      // updateCounts and batchQueryIds stay positionally aligned.
      stmt.recordBatchQueryId();
    }
  }

  private static int[] toIntUpdateCounts(long[] updateCounts) {
    int[] result = new int[updateCounts.length];
    for (int i = 0; i < updateCounts.length; i++) {
      result[i] = SnowflakeStatementImpl.toBatchInt(updateCounts[i]);
    }
    return result;
  }
}

package net.snowflake.client.internal.core.arrow.converters;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import net.snowflake.client.api.exception.ErrorCode;
import net.snowflake.client.api.resultset.SnowflakeType;
import net.snowflake.client.internal.api.implementation.exception.SFSQLException;
import net.snowflake.client.internal.util.SnowflakeUtil;
import org.apache.arrow.vector.ValueVector;

/** Converts an Arrow float64 column to REAL. */
public class DoubleToRealConverter extends AbstractArrowVectorConverter {
  private final ArrowBackedColumn floatColumn;
  private final ByteBuffer byteBuf = ByteBuffer.allocate(Double.BYTES);

  public DoubleToRealConverter(
      ValueVector fieldVector, int columnIndex, DataConversionContext context) {
    super(SnowflakeType.REAL.name(), fieldVector, columnIndex, context);
    this.floatColumn = ArrowBackedColumn.wrap(fieldVector);
  }

  @Override
  public double toDouble(int index) {
    if (isNull(index)) {
      return 0;
    }
    return floatColumn.getDouble(index);
  }

  @Override
  public byte[] toBytes(int index) {
    if (isNull(index)) {
      return null;
    }
    byteBuf.putDouble(0, toDouble(index));
    return byteBuf.array();
  }

  @Override
  public float toFloat(int index) {
    return (float) toDouble(index);
  }

  @Override
  public Object toObject(int index) {
    return isNull(index) ? null : toDouble(index);
  }

  @Override
  public String toString(int index) {
    return isNull(index) ? null : String.valueOf(toDouble(index));
  }

  @Override
  public boolean toBoolean(int index) {
    if (isNull(index)) {
      return false;
    }
    double val = toDouble(index);
    throw SFSQLException.fromErrorCode(
        ErrorCode.INVALID_VALUE_CONVERT, logicalTypeStr, SnowflakeUtil.BOOLEAN_STR, val);
  }

  @Override
  public short toShort(int rowIndex) {
    try {
      if (isNull(rowIndex)) {
        return 0;
      }
      return (short) toDouble(rowIndex);
    } catch (ClassCastException ex) {
      throw SFSQLException.fromErrorCode(
          ErrorCode.INVALID_VALUE_CONVERT,
          logicalTypeStr,
          SnowflakeUtil.SHORT_STR,
          toObject(rowIndex));
    }
  }

  @Override
  public int toInt(int rowIndex) {
    try {
      if (isNull(rowIndex)) {
        return 0;
      }
      return (int) toDouble(rowIndex);
    } catch (ClassCastException ex) {
      throw SFSQLException.fromErrorCode(
          ErrorCode.INVALID_VALUE_CONVERT,
          logicalTypeStr,
          SnowflakeUtil.INT_STR,
          toObject(rowIndex));
    }
  }

  @Override
  public long toLong(int rowIndex) {
    try {
      if (isNull(rowIndex)) {
        return 0;
      }
      return (long) toDouble(rowIndex);
    } catch (ClassCastException ex) {
      throw SFSQLException.fromErrorCode(
          ErrorCode.INVALID_VALUE_CONVERT,
          logicalTypeStr,
          SnowflakeUtil.LONG_STR,
          toObject(rowIndex));
    }
  }

  @Override
  public BigDecimal toBigDecimal(int rowIndex) {
    if (isNull(rowIndex)) {
      return null;
    }
    return BigDecimal.valueOf(toDouble(rowIndex));
  }
}

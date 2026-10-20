package net.snowflake.client.internal.core.arrow.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.snowflake.client.api.exception.ErrorCode;
import net.snowflake.client.internal.api.implementation.exception.SFSQLException;
import net.snowflake.client.internal.core.arrow.vector.Column.PhysicalKind;
import net.snowflake.client.internal.unicore.NativeArrowBatch;
import net.snowflake.client.internal.unicore.NativeArrowBatch.NativeArrowArray;
import org.junit.jupiter.api.Test;

class NativeVectorBatchTest {

  @Test
  void shouldViewPhysicalIntAndStructColumns() {
    ByteBuffer numbers = direct(8).putLong(0, 42);
    ByteBuffer epoch = direct(8).putLong(0, 7);
    NativeArrowBatch batch =
        new NativeArrowBatch(1, new NativeArrowArray[] {array(numbers), array(null, array(epoch))});

    List<Column.Field> schema =
        ColumnBatch.decodeSchema(
            schemaBytes(
                field("n", PhysicalKind.INT64, null),
                field("ts", PhysicalKind.STRUCT, field("epoch", PhysicalKind.INT64, null), 1)));

    ColumnBatch root = ColumnBatch.view(schema, batch);
    Column number = root.column("n");
    Column timestamp = root.column("ts");
    Column epochVector = timestamp.child("epoch");

    assertEquals(1, root.getRowCount());
    assertFalse(number.isNull(0));
    assertEquals(42, number.getLong(0));
    assertFalse(timestamp.isNull(0));
    assertEquals(7, epochVector.getLong(0));
    assertTrue(number.getField().isNullable());
    assertEquals(Column.PhysicalKind.STRUCT, timestamp.physicalKind());
  }

  @Test
  void shouldViewDecimalWithoutCopyingUnscaledBytesUntilRead() {
    BigDecimal value = new BigDecimal("1.00");
    ByteBuffer data = direct(16);
    byte[] unscaled = value.unscaledValue().toByteArray();
    for (int index = 0; index < unscaled.length; index++) {
      data.put(index, unscaled[unscaled.length - 1 - index]);
    }
    NativeArrowBatch batch = new NativeArrowBatch(1, new NativeArrowArray[] {array(data)});
    List<Column.Field> schema =
        ColumnBatch.decodeSchema(
            schemaBytes(field("d", PhysicalKind.DECIMAL128, null, 10, value.scale())));
    Column vector = ColumnBatch.view(schema, batch).column("d");
    assertEquals(value, vector.getDecimal(0));
  }

  @Test
  void shouldThrowWhenIsNullCalledOnClosedColumn() {
    Column column =
        ColumnBuilder.of("n", Column.PhysicalKind.INT32, Collections.<String, String>emptyMap());
    ColumnBuilder.set(column, 0, 1);
    assertTrue(column.isNull(1));
    column.close();
    assertThrows(IllegalStateException.class, () -> column.isNull(0));
  }

  @Test
  void shouldRejectFixedSizeListLengthThatOverflowsInt() {
    NativeArrowBatch batch =
        new NativeArrowBatch(2, new NativeArrowArray[] {array(null, array(direct(8)))});
    List<Column.Field> schema =
        ColumnBatch.decodeSchema(
            schemaBytes(
                field(
                    "v",
                    PhysicalKind.FIXED_SIZE_LIST,
                    field("item", PhysicalKind.INT64, null),
                    Integer.MAX_VALUE)));

    SFSQLException failure =
        assertThrows(SFSQLException.class, () -> ColumnBatch.view(schema, batch));

    SQLException sql = failure.toSQLException();
    assertEquals(ErrorCode.INTERNAL_ERROR.getMessageCode(), sql.getErrorCode());
    assertEquals("Arrow list column exceeds the supported row count", failure.getMessage());
    assertSame(ArithmeticException.class, failure.getCause().getClass());
  }

  private static NativeArrowArray array(ByteBuffer values, NativeArrowArray... children) {
    return new NativeArrowArray(null, null, values, children);
  }

  private static ByteBuffer direct(int bytes) {
    return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static byte[] schemaBytes(byte[]... fields) {
    ByteBuffer encoded = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
    encoded.putInt(fields.length);
    Arrays.stream(fields).forEach(encoded::put);
    return Arrays.copyOf(encoded.array(), encoded.position());
  }

  /** Encodes a nullable field without metadata; {@code params} precede the optional child. */
  private static byte[] field(String name, PhysicalKind kind, byte[] child, int... params) {
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    ByteBuffer encoded = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
    encoded.putInt(nameBytes.length).put(nameBytes).putInt(0).put((byte) 1);
    encoded.put((byte) kind.getTag());
    Arrays.stream(params).forEach(encoded::putInt);
    if (child != null) {
      encoded.put(child);
    }
    return Arrays.copyOf(encoded.array(), encoded.position());
  }
}

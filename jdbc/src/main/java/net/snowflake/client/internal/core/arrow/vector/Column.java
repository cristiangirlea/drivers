package net.snowflake.client.internal.core.arrow.vector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.snowflake.client.api.exception.ErrorCode;
import net.snowflake.client.internal.api.implementation.exception.SFSQLException;
import net.snowflake.client.internal.unicore.NativeArrowBatch.NativeArrowArray;

public final class Column implements AutoCloseable {
  /**
   * {@link #getTag()} matches the physical type tag written by {@code
   * jdbc_bridge/src/arrow_stream.rs}.
   */
  @Getter
  @RequiredArgsConstructor
  public enum PhysicalKind {
    BOOL(1),
    INT8(2),
    INT16(3),
    INT32(4),
    INT64(5),
    FLOAT32(6),
    FLOAT64(7),
    UTF8(8),
    BINARY(9),
    DATE32(10),
    DECIMAL128(11),
    STRUCT(12),
    FIXED_SIZE_LIST(13);

    private final int tag;

    static PhysicalKind fromTag(int tag) {
      for (PhysicalKind physicalKind : values()) {
        if (physicalKind.tag == tag) {
          return physicalKind;
        }
      }
      throw new IllegalArgumentException("Unsupported physical column tag " + tag);
    }

    boolean isVariableWidth() {
      return this == UTF8 || this == BINARY;
    }
  }

  @Value
  public static class Field {
    String name;
    PhysicalKind physicalKind;
    boolean nullable;
    Map<String, String> metadata;
    List<Field> children;
    int precision;
    int scale;
    int listSize;
  }

  @Getter private final Field field;
  private final List<Column> children;

  ByteBuffer validity;
  ByteBuffer values;
  ByteBuffer offsets;
  int valueCount;
  int filledThrough;
  boolean nativeBound;
  private boolean closed;

  Column(Field field, List<Column> children) {
    this.field = field;
    this.children = children;
    clear();
  }

  public PhysicalKind physicalKind() {
    return field.physicalKind;
  }

  public Column child(String name) {
    return children.stream().filter(c -> c.field.name.equals(name)).findFirst().orElse(null);
  }

  public boolean isNull(int index) {
    checkOpen();
    if (index < 0 || index >= valueCount) {
      return true;
    }
    return validity != null && ((validity.get(index >> 3) >> (index & 7)) & 1) == 0;
  }

  public byte getByte(int index) {
    checkOpen();
    return values.get(index);
  }

  public short getShort(int index) {
    checkOpen();
    return values.getShort(index * Short.BYTES);
  }

  public int getInt(int index) {
    checkOpen();
    return values.getInt(index * Integer.BYTES);
  }

  public long getLong(int index) {
    checkOpen();
    return values.getLong(index * Long.BYTES);
  }

  public float getFloat(int index) {
    checkOpen();
    return values.getFloat(index * Float.BYTES);
  }

  public double getDouble(int index) {
    checkOpen();
    return values.getDouble(index * Double.BYTES);
  }

  public int getBit(int index) {
    checkOpen();
    return (values.get(index >> 3) >> (index & 7)) & 1;
  }

  public long getSigned(int index) {
    switch (field.physicalKind) {
      case BOOL:
        return getBit(index);
      case INT8:
        return getByte(index);
      case INT16:
        return getShort(index);
      case INT32:
      case DATE32:
        return getInt(index);
      case INT64:
        return getLong(index);
      default:
        throw new IllegalStateException("Not an integer column: " + field.physicalKind);
    }
  }

  public byte[] getBytes(int index) {
    if (isNull(index)) {
      return null;
    }
    int start = offsets.getInt(index * Integer.BYTES);
    int end = offsets.getInt((index + 1) * Integer.BYTES);
    byte[] value = new byte[end - start];
    ByteBuffer view = values.duplicate();
    view.position(start);
    view.get(value);
    return value;
  }

  public BigDecimal getDecimal(int index) {
    if (isNull(index)) {
      return null;
    }
    int width = 16;
    byte[] bigEndian = new byte[width];
    for (int offset = 0; offset < width; offset++) {
      bigEndian[width - 1 - offset] = values.get(index * width + offset);
    }
    return new BigDecimal(new BigInteger(bigEndian), field.scale);
  }

  public Object getObject(int index) {
    switch (field.physicalKind) {
      case STRUCT:
        return structValue(index);
      case FIXED_SIZE_LIST:
        return listValue(index);
      case DECIMAL128:
        return getDecimal(index);
      default:
        // TODO(SNOW-4248904): Compile-time exhaustiveness remains deferred until the Arrow Java
        // removal is complete.
        throw new IllegalStateException("Column " + field.name + " has no object value");
    }
  }

  void bind(NativeArrowArray array, int rows) {
    checkOpen();
    validity = array.getValidity() == null ? null : littleEndian(array.getValidity());
    values = array.getValues() == null ? emptyBuffer() : littleEndian(array.getValues());
    if (field.physicalKind.isVariableWidth()) {
      offsets = array.getOffsets() == null ? emptyBuffer() : littleEndian(array.getOffsets());
      filledThrough = rows;
    }
    valueCount = rows;
    nativeBound = true;
    NativeArrowArray[] childArrays = array.getChildren();
    for (int index = 0; index < children.size(); index++) {
      Column child = children.get(index);
      if (index >= childArrays.length || childArrays[index] == null) {
        child.clear();
      } else {
        child.bind(childArrays[index], childRows(rows));
      }
    }
  }

  void setValueCountWithChildren(int count) {
    valueCount = count;
    for (Column child : children) {
      child.setValueCountWithChildren(childRows(count));
    }
  }

  private int childRows(int rows) {
    if (field.physicalKind != PhysicalKind.FIXED_SIZE_LIST) {
      return rows;
    }
    try {
      return Math.multiplyExact(rows, field.listSize);
    } catch (ArithmeticException overflow) {
      SFSQLException failure =
          new SFSQLException(
              ErrorCode.INTERNAL_ERROR, "Arrow list column exceeds the supported row count");
      failure.initCause(overflow);
      throw failure;
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    clear();
    offsets = emptyBuffer();
    children.forEach(Column::close);
  }

  private Map<String, Object> structValue(int index) {
    if (isNull(index)) {
      return null;
    }
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    for (Column child : children) {
      value.put(child.field.name, child.elementValue(index));
    }
    return value;
  }

  private List<?> listValue(int index) {
    if (isNull(index)) {
      return null;
    }
    List<Object> value = new CompactList<Object>(field.listSize);
    Column child = children.get(0);
    int start = index * field.listSize;
    for (int offset = 0; offset < field.listSize; offset++) {
      value.add(child.elementValue(start + offset));
    }
    return value;
  }

  private Object elementValue(int index) {
    if (isNull(index)) {
      return null;
    }
    switch (field.physicalKind) {
      case BOOL:
        return getBit(index) != 0;
      case INT8:
        return getByte(index);
      case INT16:
        return getShort(index);
      case INT32:
      case DATE32:
        return getInt(index);
      case INT64:
        return getLong(index);
      case FLOAT32:
        return getFloat(index);
      case FLOAT64:
        return getDouble(index);
      case UTF8:
      case BINARY:
        return getBytes(index);
      case DECIMAL128:
        return getDecimal(index);
      default:
        throw new IllegalArgumentException("Unsupported struct child " + field.physicalKind);
    }
  }

  void clear() {
    valueCount = 0;
    validity = null;
    nativeBound = false;
    values = emptyBuffer();
    filledThrough = 0;
    offsets = field.physicalKind.isVariableWidth() ? emptyBuffer(Integer.BYTES) : emptyBuffer();
  }

  void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Arrow vector is closed");
    }
  }

  static Column fromField(Field field) {
    return new Column(
        field, field.children.stream().map(Column::fromField).collect(Collectors.toList()));
  }

  static Field field(
      String name,
      PhysicalKind physicalKind,
      boolean nullable,
      Map<String, String> metadata,
      List<Field> children,
      int precision,
      int scale,
      int listSize) {
    Map<String, String> stored =
        metadata == null
            ? Collections.<String, String>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
    return new Field(
        name,
        physicalKind,
        nullable,
        stored,
        Collections.unmodifiableList(new ArrayList<Field>(children)),
        precision,
        scale,
        listSize);
  }

  private static ByteBuffer littleEndian(ByteBuffer buffer) {
    ByteBuffer view = buffer.duplicate();
    view.clear();
    return view.order(ByteOrder.LITTLE_ENDIAN);
  }

  static ByteBuffer emptyBuffer() {
    return emptyBuffer(0);
  }

  static ByteBuffer emptyBuffer(int capacity) {
    return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static final class CompactList<E> extends ArrayList<E> {
    private CompactList(int initialCapacity) {
      super(initialCapacity);
    }

    @Override
    public String toString() {
      return stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }
  }
}

package net.snowflake.client.internal.unicore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One exported batch. Its direct buffers alias the stream's current native {@code RecordBatch} and
 * are invalid after the next {@link NativeArrowStream#nextBatch()} or {@link
 * NativeArrowStream#close()}. A {@code nextBatch()} that returns null at the end of the stream
 * drops the previous batch too.
 */
@Getter
@RequiredArgsConstructor
public final class NativeArrowBatch {
  private final int rowCount;
  private final NativeArrowArray[] columns;

  static NativeArrowBatch fromNative(Object raw) {
    if (!(raw instanceof Object[])) {
      throw new IllegalStateException("Native Arrow batch must be an Object[]");
    }
    Object[] parts = (Object[]) raw;
    if (parts.length != 2 || !(parts[0] instanceof Integer) || !(parts[1] instanceof Object[])) {
      throw new IllegalStateException("Native Arrow batch has unexpected shape");
    }
    Object[] columnParts = (Object[]) parts[1];
    NativeArrowArray[] columns = new NativeArrowArray[columnParts.length];
    for (int index = 0; index < columnParts.length; index++) {
      columns[index] = NativeArrowArray.fromNative(columnParts[index]);
    }
    return new NativeArrowBatch((Integer) parts[0], columns);
  }

  @Getter
  @RequiredArgsConstructor
  public static final class NativeArrowArray {
    private final ByteBuffer validity;
    private final ByteBuffer offsets;
    private final ByteBuffer values;
    private final NativeArrowArray[] children;

    static NativeArrowArray fromNative(Object raw) {
      if (!(raw instanceof Object[])) {
        throw new IllegalStateException("Native Arrow array must be an Object[]");
      }
      Object[] parts = (Object[]) raw;
      if (parts.length != 4 || !(parts[3] instanceof Object[])) {
        throw new IllegalStateException("Native Arrow array has unexpected shape");
      }
      Object[] childParts = (Object[]) parts[3];
      NativeArrowArray[] children = new NativeArrowArray[childParts.length];
      for (int index = 0; index < childParts.length; index++) {
        children[index] = fromNative(childParts[index]);
      }
      return new NativeArrowArray(
          asBuffer(parts[0]), asBuffer(parts[1]), asBuffer(parts[2]), children);
    }

    private static ByteBuffer asBuffer(Object value) {
      if (value == null) {
        return null;
      }
      if (!(value instanceof ByteBuffer)) {
        throw new IllegalStateException("Native Arrow buffer must be a ByteBuffer");
      }
      ByteBuffer buffer = (ByteBuffer) value;
      if (!buffer.isDirect()) {
        throw new IllegalStateException("Native Arrow buffer must be direct");
      }
      // NewDirectByteBuffer leaves the buffer big-endian. Arrow C data is native-endian.
      buffer.order(ByteOrder.nativeOrder());
      // TODO(SNOW-4248904): return buffer.asReadOnlyBuffer() so Java cannot write into the native
      // batch.
      return buffer;
    }
  }
}

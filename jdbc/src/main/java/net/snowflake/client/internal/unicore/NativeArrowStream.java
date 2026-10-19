package net.snowflake.client.internal.unicore;

import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.snowflake.client.api.exception.ErrorCode;
import net.snowflake.client.internal.api.implementation.exception.SFSQLException;

/**
 * Owns an Arrow C stream inside {@code jdbc_bridge}. Direct buffers from {@link #nextBatch()} alias
 * the current native {@code RecordBatch} and are invalid after the next {@link #nextBatch()} or
 * {@link #close()}.
 *
 * <p>TODO(SNOW-4248904): {@code nextBatch}, {@code schema}, and {@code close} are unsynchronized.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class NativeArrowStream implements AutoCloseable {
  private long handle;

  /**
   * Registers {@code pointer}. The caller passes {@code 0} or the result of {@code Box::into_raw}
   * for an {@code FFI_ArrowArrayStream}, and does not free {@code pointer} after this returns. This
   * consumes the allocation, including when the native reader fails. A null or misaligned pointer
   * is not consumed.
   */
  public static NativeArrowStream open(long pointer) {
    NativeLibraryLoader.init();
    long handle = wrap(() -> nativeOpen(pointer));
    if (handle == 0) {
      throw arrowFailure(new IllegalStateException("Native Arrow stream did not return a handle"));
    }
    return new NativeArrowStream(handle);
  }

  /**
   * Returns the schema in the private little-endian layout from {@code encode_schema}: a field
   * count, then each field's name, metadata, nullability, and type tag, followed by the decimal,
   * struct, or fixed-size list payload. These bytes are not an Arrow IPC message.
   */
  public byte[] schema() {
    if (handle == 0) {
      throw closedStream();
    }
    return wrap(() -> nativeSchema(handle));
  }

  public NativeArrowBatch nextBatch() {
    if (handle == 0) {
      throw closedStream();
    }
    return wrap(
        () -> {
          Object raw = nativeNextBatch(handle);
          return raw == null ? null : NativeArrowBatch.fromNative(raw);
        });
  }

  @Override
  public void close() {
    if (handle == 0) {
      return;
    }
    nativeClose(handle);
    handle = 0;
  }

  private static <T> T wrap(Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalStateException failure) {
      throw arrowFailure(failure);
    }
  }

  private static native long nativeOpen(long pointer);

  private static native byte[] nativeSchema(long handle);

  private static native Object nativeNextBatch(long handle);

  private static native void nativeClose(long handle);

  // Matches arrow_stream::UNSUPPORTED_COLUMN_TAG.
  private static final String UNSUPPORTED_COLUMN_TAG = "UNSUPPORTED_COLUMN:";

  private static SFSQLException closedStream() {
    return arrowFailure(new IllegalStateException("Native Arrow stream is closed"));
  }

  static SFSQLException arrowFailure(IllegalStateException failure) {
    String message = failure.getMessage() == null ? "Arrow stream failed" : failure.getMessage();
    ErrorCode code;
    String detail;
    if (message.startsWith(UNSUPPORTED_COLUMN_TAG)) {
      code = ErrorCode.FEATURE_UNSUPPORTED;
      detail = message.substring(UNSUPPORTED_COLUMN_TAG.length());
    } else {
      code = ErrorCode.INTERNAL_ERROR;
      detail = message;
    }
    SFSQLException translated = new SFSQLException(code, detail);
    translated.initCause(failure);
    return translated;
  }
}

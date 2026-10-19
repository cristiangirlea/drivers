package net.snowflake.client.internal.unicore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class NativeArrowBatchTest {

  @Test
  void shouldSetNativeByteOrderOnDirectBuffers() {
    ByteBuffer values = ByteBuffer.allocateDirect(4);
    Object column = new Object[] {null, null, values, new Object[0]};
    NativeArrowBatch batch = NativeArrowBatch.fromNative(new Object[] {1, new Object[] {column}});

    NativeArrowBatch.NativeArrowArray exported = batch.getColumns()[0];
    assertNull(exported.getValidity());
    assertNull(exported.getOffsets());
    assertSame(values, exported.getValues());
    assertEquals(ByteOrder.nativeOrder(), exported.getValues().order());
  }
}

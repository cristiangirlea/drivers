package net.snowflake.client.internal.core.arrow.converters;

import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.complex.StructVector;

/**
 * Reads the Arrow vector a converter already holds. That vector is reused across batches, so each
 * call reads the batch currently loaded into it.
 */
final class ArrowBackedColumn {
  private final ValueVector vector;

  private ArrowBackedColumn(ValueVector vector) {
    this.vector = vector;
  }

  static ArrowBackedColumn wrap(ValueVector vector) {
    return new ArrowBackedColumn(vector);
  }

  boolean isNull(int index) {
    return vector.isNull(index);
  }

  ArrowBackedColumn child(String name, Class<? extends FieldVector> type) {
    return wrap(((StructVector) vector).getChild(name, type));
  }

  long getSigned(int index) {
    if (vector instanceof TinyIntVector) {
      return getByte(index);
    }
    if (vector instanceof SmallIntVector) {
      return getShort(index);
    }
    if (vector instanceof IntVector) {
      return getInt(index);
    }
    return getLong(index);
  }

  byte getByte(int index) {
    TinyIntVector values = (TinyIntVector) vector;
    return values.getDataBuffer().getByte((long) index * TinyIntVector.TYPE_WIDTH);
  }

  short getShort(int index) {
    SmallIntVector values = (SmallIntVector) vector;
    return values.getDataBuffer().getShort((long) index * SmallIntVector.TYPE_WIDTH);
  }

  int getInt(int index) {
    IntVector values = (IntVector) vector;
    return values.getDataBuffer().getInt((long) index * IntVector.TYPE_WIDTH);
  }

  long getLong(int index) {
    BigIntVector values = (BigIntVector) vector;
    return values.getDataBuffer().getLong((long) index * BigIntVector.TYPE_WIDTH);
  }
}

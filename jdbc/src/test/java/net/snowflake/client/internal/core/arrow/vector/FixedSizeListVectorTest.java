package net.snowflake.client.internal.core.arrow.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedSizeListVectorTest {

  @Test
  void shouldFormatIntVectorWithoutSpaces() {
    Column vector = intVector(1, 2, 3);
    List<?> values = (List<?>) vector.getObject(0);

    assertEquals(Arrays.asList(1, 2, 3), values);
    assertEquals(1, values.get(0));
    assertEquals("[1,2,3]", values.toString());
    assertEquals(
        "[-2147483648,2147483647,0]",
        intVector(Integer.MIN_VALUE, Integer.MAX_VALUE, 0).getObject(0).toString());
  }

  @Test
  void shouldFormatFloatVectorWithoutSpaces() {
    Column vector = floatVector(1.8f, -3.4f, 6.7f, 0.0f, 2.3f);
    List<?> values = (List<?>) vector.getObject(0);

    assertEquals(Arrays.asList(1.8f, -3.4f, 6.7f, 0.0f, 2.3f), values);
    assertEquals("[1.8,-3.4,6.7,0.0,2.3]", values.toString());
  }

  @Test
  void shouldFormatEmptyVectorAsEmptyList() {
    Column vector = intVector();

    assertEquals(Collections.emptyList(), vector.getObject(0));
    assertEquals("[]", vector.getObject(0).toString());
  }

  @Test
  void shouldReturnNullForNullVector() {
    Column vector = intVector(1, 2, 3);
    ColumnBuilder.setNull(vector, 0);

    assertNull(vector.getObject(0));
  }

  private static Column intVector(int... values) {
    Column child = ColumnBuilder.of("item", Column.PhysicalKind.INT32, null);
    for (int index = 0; index < values.length; index++) {
      ColumnBuilder.set(child, index, values[index]);
    }
    return list(child, values.length);
  }

  private static Column floatVector(float... values) {
    Column child = ColumnBuilder.of("item", Column.PhysicalKind.FLOAT32, null);
    for (int index = 0; index < values.length; index++) {
      ColumnBuilder.set(child, index, values[index]);
    }
    return list(child, values.length);
  }

  private static Column list(Column child, int listSize) {
    Column vector = ColumnBuilder.list("v", child, listSize);
    ColumnBuilder.setDefined(vector, 0);
    return vector;
  }
}

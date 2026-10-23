package net.snowflake.client.internal.core.arrow.converters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.snowflake.client.internal.core.arrow.TestHelper;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.jupiter.api.Test;

public class DecfloatToDecimalConverterTest extends BaseConverterTest {
  private final BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);

  @Test
  public void testDecfloatConversionsAndDispatch() throws Exception {
    Schema schema = setupSchema();

    try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
      StructVector vector = (StructVector) root.getVector("col_one");
      VarBinaryVector significand = (VarBinaryVector) vector.getChild("significand");
      SmallIntVector exponent = (SmallIntVector) vector.getChild("exponent");

      vector.allocateNew();
      significand.setSafe(0, new BigInteger("123456").toByteArray());
      exponent.setSafe(0, (short) -3);
      vector.setIndexDefined(0);

      vector.setNull(1);

      significand.setSafe(2, new BigInteger("2147483648").toByteArray());
      exponent.setSafe(2, (short) 0);
      vector.setIndexDefined(2);

      significand.setSafe(3, new BigInteger("9223372036854775808").toByteArray());
      exponent.setSafe(3, (short) 0);
      vector.setIndexDefined(3);

      significand.setSafe(4, new BigInteger("12345").toByteArray());
      exponent.setSafe(4, (short) -2);
      vector.setIndexDefined(4);

      vector.setValueCount(5);
      root.setRowCount(5);

      ArrowVectorConverter converter = new DecfloatToDecimalConverter(vector, 0, this);
      ArrowVectorConverter dispatchedConverter =
          ArrowVectorConverterUtil.initConverter(vector, this, 0);
      assertInstanceOf(DecfloatToDecimalConverter.class, dispatchedConverter);

      assertEquals(new BigDecimal("123.456"), converter.toBigDecimal(0));
      assertEquals(new BigDecimal("123.456"), converter.toObject(0));
      assertEquals("123.456", converter.toString(0));
      assertEquals(123.456d, converter.toDouble(0), 0.001d);
      assertEquals(123.456f, converter.toFloat(0), 0.001f);

      assertNull(converter.toBigDecimal(1));
      assertNull(converter.toObject(1));
      assertNull(converter.toString(1));
      assertEquals(0d, converter.toDouble(1), 0d);
      assertEquals(0f, converter.toFloat(1), 0f);
      assertEquals(0L, converter.toLong(1));
      assertEquals(0, converter.toInt(1));
      assertEquals((short) 0, converter.toShort(1));

      assertEquals(2147483648L, converter.toLong(2));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toInt(2));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toShort(2));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toLong(3));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toInt(4));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toShort(4));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toLong(4));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toBytes(0));
      TestHelper.assertSFException(invalidConversionErrorCode, () -> converter.toBoolean(0));
    }
  }

  @Test
  public void shouldFormatZeroAndPlainDecfloatWithinThirtyEightCharacters() throws Exception {
    assertDecfloatStrings(
        new BigDecimal[] {
          BigDecimal.ZERO,
          new BigDecimal("123.456"),
          new BigDecimal("1.23E-7"),
          new BigDecimal("-42"),
          new BigDecimal("-1.5"),
          new BigDecimal("-789.012"),
          new BigDecimal(BigInteger.valueOf(-987), 17),
        },
        new String[] {
          "0", "123.456", "0.000000123", "-42", "-1.5", "-789.012", "-0.00000000000000987",
        });
  }

  @Test
  public void shouldFormatWholeNumberDecfloatAsPlainDecimal() throws Exception {
    assertDecfloatStrings(
        new BigDecimal[] {
          new BigDecimal(BigInteger.ONE, -2),
          new BigDecimal(BigInteger.ONE, -6),
          new BigDecimal("100"),
          new BigDecimal("1000000"),
        },
        new String[] {"100", "1000000", "100", "1000000"});
  }

  @Test
  public void shouldFormatDecfloatInNormalizedScientificNotationPastThirtyEightCharacters()
      throws Exception {
    BigDecimal thirtyEightDigitInteger = new BigDecimal(BigInteger.TEN.pow(37));
    BigDecimal thirtyEightSigWithFraction =
        new BigDecimal(BigInteger.TEN.pow(37).add(BigInteger.valueOf(8)), 1);
    assertDecfloatStrings(
        new BigDecimal[] {
          new BigDecimal("1.2e200"),
          new BigDecimal("1E+16384"),
          new BigDecimal("1.2345678901234567890123456789012345678E+100"),
          new BigDecimal("-1.234E+8000"),
          new BigDecimal(BigInteger.valueOf(123), -35),
          new BigDecimal(BigInteger.valueOf(123), -36),
          new BigDecimal(BigInteger.ONE, 36),
          new BigDecimal(BigInteger.ONE, 37),
          thirtyEightDigitInteger,
          thirtyEightSigWithFraction,
        },
        new String[] {
          "1.2e200",
          "1e16384",
          "1.2345678901234567890123456789012345678e100",
          "-1.234e8000",
          "12300000000000000000000000000000000000",
          "1.23e38",
          "0.000000000000000000000000000000000001",
          "1e-37",
          thirtyEightDigitInteger.toPlainString(),
          "1." + zeros(36) + "8e36",
        });
  }

  @Test
  public void shouldFormatDecfloatToStringWithoutChangingNumericValue() throws Exception {
    BigDecimal scientific = new BigDecimal("1.2e200");
    BigDecimal plain = new BigDecimal("123.456");
    BigDecimal hundred = new BigDecimal(BigInteger.ONE, -2);
    BigDecimal million = new BigDecimal(BigInteger.ONE, -6);
    try (VectorSchemaRoot root = VectorSchemaRoot.create(setupSchema(), allocator)) {
      ArrowVectorConverter converter =
          loadDecfloat(root, scientific, plain, hundred, million, BigDecimal.ZERO, null);
      assertEquals("1.2e200", converter.toString(0));
      assertEquals("123.456", converter.toString(1));
      assertEquals("100", converter.toString(2));
      assertEquals("1000000", converter.toString(3));
      assertEquals("0", converter.toString(4));
      assertNull(converter.toString(5));
      assertEquals(scientific, converter.toBigDecimal(0));
      assertEquals(plain, converter.toBigDecimal(1));
      assertEquals(0, new BigDecimal("100").compareTo(converter.toBigDecimal(2)));
      assertEquals(0, new BigDecimal("1000000").compareTo(converter.toBigDecimal(3)));
    }
  }

  private void assertDecfloatStrings(BigDecimal[] values, String[] expected) throws Exception {
    try (VectorSchemaRoot root = VectorSchemaRoot.create(setupSchema(), allocator)) {
      ArrowVectorConverter converter = loadDecfloat(root, values);
      for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], converter.toString(i));
      }
    }
  }

  private ArrowVectorConverter loadDecfloat(VectorSchemaRoot root, BigDecimal... values) {
    StructVector vector = (StructVector) root.getVector("col_one");
    VarBinaryVector significand = (VarBinaryVector) vector.getChild("significand");
    SmallIntVector exponent = (SmallIntVector) vector.getChild("exponent");
    vector.allocateNew();
    for (int i = 0; i < values.length; i++) {
      if (values[i] == null) {
        vector.setNull(i);
      } else {
        significand.setSafe(i, values[i].unscaledValue().toByteArray());
        exponent.setSafe(i, (short) -values[i].scale());
        vector.setIndexDefined(i);
      }
    }
    vector.setValueCount(values.length);
    root.setRowCount(values.length);
    return new DecfloatToDecimalConverter(vector, 0, this);
  }

  private static String zeros(int count) {
    StringBuilder out = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      out.append('0');
    }
    return out.toString();
  }

  private static Schema setupSchema() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("logicalType", "DECFLOAT");

    Field significandField =
        new Field("significand", new FieldType(true, ArrowType.Binary.INSTANCE, null, null), null);
    Field exponentField =
        new Field("exponent", new FieldType(true, new ArrowType.Int(16, true), null, null), null);
    Field decfloatField =
        new Field(
            "col_one",
            new FieldType(true, ArrowType.Struct.INSTANCE, null, metadata),
            Arrays.asList(significandField, exponentField));

    return new Schema(Collections.singletonList(decfloatField));
  }
}

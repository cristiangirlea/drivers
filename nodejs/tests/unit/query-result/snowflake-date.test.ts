import { describe, expect, it } from 'vitest';
import { SnowflakeDate } from '../../../src/query-result/SnowflakeDate.js';

describe('SnowflakeDate', () => {
  it('should throw a plain Error without a code when the timezone is unknown', () => {
    const date = new SnowflakeDate(0, 0, 0, 'Not/AZone', 'YYYY-MM-DD');

    expect(() => date.toJSON()).toThrow(
      expect.objectContaining({
        name: 'Error',
        message: 'unknown timezone: Not/AZone',
        code: undefined,
      }),
    );
  });
});

import { describe, it, expect } from 'vitest';
import ErrorCode from '../../src/constants/ErrorCode.js';
import { snowflake } from './utils/index.js';

describe('SDK Constants', () => {
  it('exports ErrrorCode', () => {
    expect(snowflake.ErrorCode).toEqual(ErrorCode);
  });
});

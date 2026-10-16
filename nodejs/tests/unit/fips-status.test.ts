import { expect, it, describe } from 'vitest';
import { getFipsStatus } from '../../src/index.js';

describe('getFipsStatus', () => {
  it('should return isFips=false for default build', () => {
    const status = getFipsStatus();
    expect(status).toEqual({
      isFips: false,
    });
  });

  // TODO:
  // This test is uselless, we don't have a runner that actually sets SF_CORE_EXPECT_FIPS
  it.skip('should isFips=true when build is produced with --fips', () => {
    const fipsMode = process.env.SF_CORE_EXPECT_FIPS?.toLowerCase();
    const expectedFips = fipsMode === '1' || fipsMode === 'true';
    const status = getFipsStatus();

    expect(status).toEqual({
      isFips: expectedFips,
    });
  });
});

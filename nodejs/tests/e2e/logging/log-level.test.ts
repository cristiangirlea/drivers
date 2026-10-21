import { describe, expect, it } from 'vitest';
import { isRunningNewDriverWithBD, snowflake } from '../utils/index.js';

describe('logLevel', () => {
  it('should reject an unknown logLevel', () => {
    const configure = () => snowflake.configure({ logLevel: 'INVALID' as 'INFO' });

    if (isRunningNewDriverWithBD('BD#134')) {
      expect(configure).toThrow(
        expect.objectContaining({
          name: 'InvalidParameterError',
          code: undefined,
          message: 'Invalid logLevel: INVALID',
        }),
      );
    } else {
      expect(configure).toThrow(
        expect.objectContaining({
          name: 'InvalidParameterError',
          code: 403001,
          message:
            'Invalid logLevel. The specified value must be one of these five levels: error, warn, debug, info and trace.',
        }),
      );
    }
  });
});

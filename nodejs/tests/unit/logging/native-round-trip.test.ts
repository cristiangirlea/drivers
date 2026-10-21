import { describe, expect, it, onTestFinished, vi } from 'vitest';
import '../../../src/index.js';
import { resetGlobalConfig, updateGlobalConfig } from '../../../src/global-config.js';
import { logger, type SnowflakeLogger } from '../../../src/logging/logger.js';

describe('native logEvent round-trip', () => {
  it('should deliver wrapper logs through the native pipeline when the bridge is initialized', async () => {
    const logs = createCapturedLogs();

    logger.info('native pipeline %s', 'check');

    await vi.waitUntil(() => logs.some((log) => log.message === 'native pipeline check'), {
      timeout: 2000,
    });
    expect(logs).toContainEqual({
      level: 'info',
      message: 'native pipeline check',
    });
  });
});

interface CapturedLog {
  level: keyof SnowflakeLogger;
  message: string;
}

function createCapturedLogs(): CapturedLog[] {
  const logs: CapturedLog[] = [];
  const capture = (level: keyof SnowflakeLogger) => (message: string) =>
    logs.push({ level, message });
  updateGlobalConfig({
    customLogger: {
      error: capture('error'),
      warn: capture('warn'),
      info: capture('info'),
      debug: capture('debug'),
      trace: capture('trace'),
    },
  });
  onTestFinished(() => resetGlobalConfig());
  return logs;
}

import { onTestFinished, vi } from 'vitest';

export function createStreamCapture(stream: NodeJS.WriteStream): string[] {
  const writes: string[] = [];
  const spy = vi.spyOn(stream, 'write').mockImplementation((chunk) => {
    writes.push(String(chunk));
    return true;
  });
  onTestFinished(() => spy.mockRestore());
  return writes;
}

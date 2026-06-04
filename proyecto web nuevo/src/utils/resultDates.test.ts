import { describe, expect, it } from 'vitest';
import { sameResultDay, toDateInputValue, toResultCacheDateKey } from './resultDates';

describe('result date helpers', () => {
  it('converts date input values to result cache keys', () => {
    expect(toResultCacheDateKey('2026-06-01')).toBe('01-06-2026');
  });

  it('keeps existing result cache keys unchanged', () => {
    expect(toResultCacheDateKey('01-06-2026')).toBe('01-06-2026');
  });

  it('converts result cache keys back to date input values', () => {
    expect(toDateInputValue('01-06-2026')).toBe('2026-06-01');
  });

  it('compares manual ISO dates with cache dates as the same day', () => {
    expect(sameResultDay('2026-06-01', '01-06-2026')).toBe(true);
  });
});

const RESULT_CACHE_DATE_PATTERN = /^(\d{2})-(\d{2})-(\d{4})$/;
const INPUT_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

export const toResultCacheDateKey = (inputDate: string): string => {
  const trimmed = String(inputDate || '').trim();
  const inputMatch = trimmed.match(INPUT_DATE_PATTERN);
  if (inputMatch) {
    const [, yyyy, mm, dd] = inputMatch;
    return `${dd}-${mm}-${yyyy}`;
  }

  const cacheMatch = trimmed.match(RESULT_CACHE_DATE_PATTERN);
  if (cacheMatch) {
    return trimmed;
  }

  return trimmed;
};

export const toDateInputValue = (dateKey: string): string => {
  const trimmed = String(dateKey || '').trim();
  const cacheMatch = trimmed.match(RESULT_CACHE_DATE_PATTERN);
  if (cacheMatch) {
    const [, dd, mm, yyyy] = cacheMatch;
    return `${yyyy}-${mm}-${dd}`;
  }

  return trimmed;
};

export const getTodayInputDateDR = (): string => {
  const now = new Date();
  const drTime = new Date(now.getTime() - 4 * 60 * 60 * 1000);
  const yyyy = drTime.getUTCFullYear();
  const mm = String(drTime.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(drTime.getUTCDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
};

export const getTodayResultDateKeyDR = (): string => {
  return toResultCacheDateKey(getTodayInputDateDR());
};

export const sameResultDay = (left: string, right: string): boolean => {
  return toResultCacheDateKey(left) === toResultCacheDateKey(right);
};

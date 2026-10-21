export const SUPPORTED_LOCALES = ['zh-CN', 'ja-JP'] as const;

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const normalizeLocale = (value?: string | null): SupportedLocale | undefined => {
  if (!value) return undefined;
  const normalized = value.trim().toLowerCase().replace('_', '-');
  if (normalized === 'ja' || normalized.startsWith('ja-')) return 'ja-JP';
  if (normalized === 'zh' || normalized.startsWith('zh-')) return 'zh-CN';
  return undefined;
};

export const resolveLocale = (
  search: string,
  storedLocale: string | null,
  browserLocales: readonly string[],
): SupportedLocale => {
  const fromUrl = normalizeLocale(new URLSearchParams(search).get('lang'));
  if (fromUrl) return fromUrl;

  const fromStorage = normalizeLocale(storedLocale);
  if (fromStorage) return fromStorage;

  for (const browserLocale of browserLocales) {
    const resolved = normalizeLocale(browserLocale);
    if (resolved) return resolved;
  }
  return 'zh-CN';
};

export const getInitialLocale = (): SupportedLocale => {
  if (typeof window === 'undefined') return 'zh-CN';
  const resolved = resolveLocale(
    window.location.search,
    window.localStorage.getItem('umi_locale'),
    navigator.languages?.length ? navigator.languages : [navigator.language],
  );

  // A language in a shared URL is an explicit user choice and should persist.
  if (normalizeLocale(new URLSearchParams(window.location.search).get('lang'))) {
    window.localStorage.setItem('umi_locale', resolved);
  }
  return resolved;
};

export const localeQueryValue = (locale: SupportedLocale) => locale === 'ja-JP' ? 'ja' : 'zh';

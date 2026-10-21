import { useState } from 'react';
import { getLocale, setLocale } from 'umi';
import { localeQueryValue, normalizeLocale, type SupportedLocale } from '@/i18n/locale';
import styles from './index.less';

export default function LanguageSwitcher() {
  const [activeLocale, setActiveLocale] = useState<SupportedLocale>(() => normalizeLocale(getLocale()) ?? 'zh-CN');

  const changeLocale = (nextLocale: SupportedLocale) => {
    if (nextLocale === activeLocale) return;
    setLocale(nextLocale, false);
    setActiveLocale(nextLocale);
    const url = new URL(window.location.href);
    url.searchParams.set('lang', localeQueryValue(nextLocale));
    window.history.replaceState(window.history.state, '', url);
    document.documentElement.lang = nextLocale;
  };

  return (
    <div className={styles.switcher} role="group" aria-label="语言 / 言語">
      <button
        type="button"
        className={activeLocale === 'zh-CN' ? styles.active : ''}
        aria-pressed={activeLocale === 'zh-CN'}
        onClick={() => changeLocale('zh-CN')}
      >
        中文
      </button>
      <span aria-hidden="true">/</span>
      <button
        type="button"
        className={activeLocale === 'ja-JP' ? styles.active : ''}
        aria-pressed={activeLocale === 'ja-JP'}
        onClick={() => changeLocale('ja-JP')}
      >
        日本語
      </button>
    </div>
  );
}

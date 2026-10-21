import { ConfigProvider, theme } from 'antd';
import React, { type ReactNode, useEffect } from 'react';
import { getLocale, type RequestConfig } from 'umi';
import { current } from '@/services/user';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import { getInitialLocale, normalizeLocale } from '@/i18n/locale';

export type InitialState = {
  currentUser?: API.User;
};

export async function getInitialState(): Promise<InitialState> {
  try {
    const response = await current();
    return { currentUser: response.data };
  } catch {
    return {};
  }
}

export const request: RequestConfig = {
  withCredentials: true,

  requestInterceptors: [
    (url, options) => ({
      url,
      options: {
        ...options,
        headers: {
          ...options.headers,
          'Accept-Language': normalizeLocale(getLocale()) ?? 'zh-CN',
        },
      },
    }),
  ],

  responseInterceptors: [
    (response) => {
      const body = response.data as API.BaseResponse<unknown>;

      if (body.code !== 0) {
        throw new Error(body.description || body.message);
      }

      return response;
    },
  ],
};

export const locale = {
  getLocale: getInitialLocale,
};

function AppProviders({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.documentElement.lang = normalizeLocale(getLocale()) ?? 'zh-CN';
  }, []);

  return React.createElement(
    ConfigProvider,
    {
      theme: {
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#efeee9',
          colorBgBase: '#0d0e0f',
          colorBgContainer: '#171819',
          colorBorder: 'rgba(255,255,255,0.10)',
          colorText: '#f2f1ed',
          colorTextSecondary: '#a3a3a0',
          borderRadius: 16,
          controlHeight: 48,
          fontSize: 16,
          fontFamily: 'var(--app-font-family)',
        },
        components: {
          Button: { primaryColor: '#111213', controlHeight: 50 },
          Input: { controlHeight: 54 },
        },
      },
    },
    React.createElement(LanguageSwitcher),
    children,
  );
}

export function rootContainer(container: ReactNode) {
  return React.createElement(AppProviders, null, container);
}

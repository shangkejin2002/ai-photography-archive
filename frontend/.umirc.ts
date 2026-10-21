import { defineConfig } from 'umi';
import routes from './config/routes';
import proxy from './config/proxy';

export default defineConfig({
  hash: true,
  plugins: [
    '@umijs/plugins/dist/request',
    '@umijs/plugins/dist/initial-state',
    '@umijs/plugins/dist/model',
    '@umijs/plugins/dist/access',
    '@umijs/plugins/dist/locale',
  ],
  model: {},
  initialState: {},
  access: {},
  request: {
    dataField: '',
  },
  locale: {
    default: 'zh-CN',
    baseNavigator: true,
    baseSeparator: '-',
    useLocalStorage: true,
    antd: true,
  },
  routes,
  proxy: proxy.dev,
  npmClient: 'pnpm',
});

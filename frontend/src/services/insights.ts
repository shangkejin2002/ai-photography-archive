import { request } from 'umi';

export const getShootingInsights = () =>
  request<API.BaseResponse<API.ShootingInsights>>('/api/insights');

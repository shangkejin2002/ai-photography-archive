import { getIntl, getLocale, request } from 'umi';

export const previewAICreate = (prompt: string) =>
  request<API.BaseResponse<API.AICreatePreview>>('/api/ai-create/preview', {
    method: 'POST',
    data: { prompt },
  });

export const createPortfolio = (data: {
  title: string;
  subtitle?: string;
  description?: string;
  sourcePrompt?: string;
  coverPhotoId?: number;
  photoIds: number[];
}) => request<API.BaseResponse<API.Portfolio>>('/api/portfolios', { method: 'POST', data });

export const listPortfolios = () =>
  request<API.BaseResponse<API.Portfolio[]>>('/api/portfolios');

export const getPortfolio = (portfolioId: number) =>
  request<API.BaseResponse<API.Portfolio>>(`/api/portfolios/${portfolioId}`);

export const getSharedPortfolio = (shareToken: string) =>
  request<API.BaseResponse<API.Portfolio>>(`/api/public/portfolios/${shareToken}`);

export const updatePortfolio = (portfolioId: number, data: {
  title: string;
  subtitle?: string;
  description?: string;
  coverPhotoId?: number;
  photoIds: number[];
}) => request<API.BaseResponse<API.Portfolio>>(`/api/portfolios/${portfolioId}`, { method: 'PATCH', data });

export const deletePortfolio = (portfolioId: number) =>
  request<API.BaseResponse<boolean>>(`/api/portfolios/${portfolioId}`, { method: 'DELETE' });

export const enablePortfolioSharing = (portfolioId: number) =>
  request<API.BaseResponse<API.Portfolio>>(`/api/portfolios/${portfolioId}/share`, { method: 'POST' });

export const disablePortfolioSharing = (portfolioId: number) =>
  request<API.BaseResponse<API.Portfolio>>(`/api/portfolios/${portfolioId}/share`, { method: 'DELETE' });

export const exportPortfolioPdf = async (portfolioId: number) => {
  const response = await fetch(`/api/portfolios/${portfolioId}/pdf`, {
    method: 'GET',
    credentials: 'include',
    headers: { 'Accept-Language': getLocale() },
  });
  const contentType = response.headers.get('content-type') ?? '';
  if (!response.ok || !contentType.includes('application/pdf')) {
    if (contentType.includes('application/json')) {
      const body = await response.json() as API.BaseResponse<unknown>;
      throw new Error(body.description || body.message || getIntl().formatMessage({ id: 'portfolio.pdf.failed' }));
    }
    throw new Error(getIntl().formatMessage({ id: 'portfolio.pdf.failed' }));
  }
  return response.blob();
};

import { getIntl, getLocale, request } from 'umi';

export const listPhotos = (favorite?: boolean) =>
  request<API.BaseResponse<API.Photo[]>>('/api/photo', {
    method: 'GET',
    params: favorite === undefined ? undefined : { favorite },
  });

export const getPhoto = (photoId: number) =>
  request<API.BaseResponse<API.Photo>>(`/api/photo/${photoId}`);

export const uploadPhoto = (
  file: File,
  onProgress?: (percent: number) => void,
) => new Promise<API.BaseResponse<API.Photo>>((resolve, reject) => {
  const xhr = new XMLHttpRequest();
  const data = new FormData();
  data.append('file', file);

  xhr.open('POST', '/api/photo/upload');
  xhr.withCredentials = true;
  xhr.responseType = 'json';
  xhr.setRequestHeader('Accept-Language', getLocale());

  xhr.upload.addEventListener('progress', (event) => {
    if (event.lengthComputable) {
      onProgress?.(Math.round((event.loaded / event.total) * 100));
    }
  });

  xhr.addEventListener('load', () => {
    const body = xhr.response as API.BaseResponse<API.Photo> | null;
    if (xhr.status >= 200 && xhr.status < 300 && body?.code === 0) {
      resolve(body);
      return;
    }
    reject(new Error(body?.description || body?.message || getIntl().formatMessage(
      { id: 'error.upload.http' },
      { status: xhr.status },
    )));
  });

  xhr.addEventListener('error', () => reject(new Error(getIntl().formatMessage({ id: 'error.network' }))));
  xhr.addEventListener('abort', () => reject(new Error(getIntl().formatMessage({ id: 'error.upload.cancelled' }))));
  xhr.send(data);
});

export const setPhotoFavorite = (photoId: number, favorite: boolean) =>
  request<API.BaseResponse<API.Photo>>(`/api/photo/${photoId}/favorite`, {
    method: 'PATCH',
    data: { favorite },
  });

export const deletePhoto = (photoId: number) =>
  request<API.BaseResponse<boolean>>(`/api/photo/${photoId}`, {
    method: 'DELETE',
  });

export const reprocessPhotoMetadata = () =>
  request<API.BaseResponse<number>>('/api/photo/metadata/reprocess', {
    method: 'POST',
  });

export const analyzePhoto = (photoId: number) =>
  request<API.BaseResponse<API.Photo>>(`/api/photo/${photoId}/analysis`, {
    method: 'POST',
  });

export const analyzePendingPhotos = () =>
  request<API.BaseResponse<number>>('/api/photo/analysis/pending', {
    method: 'POST',
  });

export const localizeJapanesePhotos = async (photoIds: number[]) => {
  const ids = [...new Set(photoIds)].filter((id) => Number.isFinite(id) && id > 0);
  const localized: API.Photo[] = [];
  let response: API.BaseResponse<API.Photo[]> | undefined;
  for (let index = 0; index < ids.length; index += 20) {
    response = await request<API.BaseResponse<API.Photo[]>>('/api/photo/localizations/ja', {
      method: 'POST',
      data: { photoIds: ids.slice(index, index + 20) },
    });
    localized.push(...response.data);
  }
  return response ? { ...response, data: localized } : {
    code: 0, data: localized, message: 'ok', description: null,
  };
};

export const setPhotoLocation = (photoId: number, locationName: string) =>
  request<API.BaseResponse<API.Photo>>(`/api/photo/${photoId}/location`, {
    method: 'PATCH',
    data: { locationName },
  });

import { request } from 'umi';

export const login = (data: API.LoginParams) =>
    request<API.BaseResponse<API.User>>('/api/user/login', {
        method: 'POST',
        data,
    });

export const current = () =>
    request<API.BaseResponse<API.User>>('/api/user/current');

export const logout = () =>
    request<API.BaseResponse<boolean>>('/api/user/logout', {
        method: 'POST',
    });

export const register = (data: API.RegisterParams) =>
    request<API.BaseResponse<number>>('/api/user/register', {
        method: 'POST',
        data,
    });

export const searchUsers = (username?: string) =>
    request<API.BaseResponse<API.User[]>>('/api/user/search', {
        method: 'GET',
        params: { username },
    });

export const deleteUser = (id: number) =>
    request<API.BaseResponse<boolean>>('/api/user/delete', {
        method: 'POST',
        data: id,
    });

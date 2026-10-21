import { Spin } from 'antd';
import { Navigate, Outlet, useIntl, useLocation, useModel } from 'umi';
import styles from './Auth.less';

export default function AuthWrapper() {
    const location = useLocation();
    const { formatMessage: t } = useIntl();
    const { initialState, loading } = useModel('@@initialState');

    if (loading) {
        return (
            <div className={styles.loading}>
                <Spin size="large" />
                <span>{t({ id: 'auth.opening' })}</span>
            </div>
        );
    }

    if (!initialState?.currentUser?.id) {
        const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
        return <Navigate to={`/user/login?redirect=${redirect}`} replace />;
    }

    return <Outlet />;
}

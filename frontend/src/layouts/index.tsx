import { Button, message } from 'antd';
import { useState } from 'react';
import { history, Link, Outlet, useIntl, useLocation, useModel } from 'umi';
import { logout } from '@/services/user';
import styles from './index.less';

export default function Layout() {
  const [loggingOut, setLoggingOut] = useState(false);
  const { formatMessage: t } = useIntl();
  const location = useLocation();
  const { initialState, setInitialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser;

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
      await setInitialState({});
      message.success(t({ id: 'nav.logout.success' }));
      history.replace('/user/login');
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'nav.logout.failed' }));
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <Link className={styles.brand} to="/library">Photography Archive</Link>
        <nav className={styles.navigation} aria-label={t({ id: 'nav.main.aria' })}>
          <Link className={location.pathname.startsWith('/library') || location.pathname.startsWith('/photos/') ? styles.active : ''} to="/library">Library</Link>
          <Link className={location.pathname.startsWith('/ai-create') ? styles.active : ''} to="/ai-create">AI Create</Link>
          <Link className={location.pathname.startsWith('/portfolios') ? styles.active : ''} to="/portfolios">Portfolios</Link>
          <Link className={location.pathname.startsWith('/insights') ? styles.active : ''} to="/insights">Insights</Link>
        </nav>
        <div className={styles.account}>
          {currentUser ? (
            <>
              {currentUser.userRole === 1 && <Link className={styles.adminLink} to="/admin/user">Admin</Link>}
              <Link className={styles.profileLink} to="/profile">
                <span className={styles.avatar}>{(currentUser.username || currentUser.userAccount).slice(0, 2).toUpperCase()}</span>
                <span>{currentUser.username || currentUser.userAccount}</span>
              </Link>
              <Button className={styles.logout} type="text" loading={loggingOut} onClick={handleLogout}>{t({ id: 'nav.logout' })}</Button>
            </>
          ) : (
            <div className={styles.guestLinks}>
              <Link to="/user/login">{t({ id: 'nav.login' })}</Link>
              <Link to="/user/register">{t({ id: 'nav.register' })}</Link>
            </div>
          )}
        </div>
      </header>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}

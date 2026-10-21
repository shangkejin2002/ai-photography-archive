import { Descriptions } from 'antd';
import { useIntl, useModel } from 'umi';
import styles from './index.less';

export default function ProfilePage() {
  const { initialState } = useModel('@@initialState');
  const { formatMessage: t } = useIntl();
  const user = initialState?.currentUser;
  if (!user) return null;

  return (
    <section className={styles.page}>
      <p className={styles.kicker}>{t({ id: 'profile.kicker' })}</p>
      <h1>{user.username || user.userAccount}</h1>
      <div className={styles.panel}>
        <Descriptions column={1} colon={false}>
          <Descriptions.Item label={t({ id: 'profile.account' })}>{user.userAccount}</Descriptions.Item>
          <Descriptions.Item label={t({ id: 'profile.role' })}>{user.userRole === 1 ? t({ id: 'profile.role.admin' }) : t({ id: 'profile.role.photographer' })}</Descriptions.Item>
          <Descriptions.Item label={t({ id: 'profile.status' })}>{user.userStatus === 0 ? t({ id: 'profile.status.active' }) : t({ id: 'profile.status.unavailable' })}</Descriptions.Item>
        </Descriptions>
      </div>
    </section>
  );
}

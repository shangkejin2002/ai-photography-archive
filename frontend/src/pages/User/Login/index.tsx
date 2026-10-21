import { Button, Form, Input, message } from 'antd';
import { useState } from 'react';
import { history, Link, useIntl, useLocation, useModel } from 'umi';
import { login } from '@/services/user';
import styles from '../auth.less';

export default function LoginPage() {
    const [submitting, setSubmitting] = useState(false);
    const { formatMessage: t } = useIntl();
    const location = useLocation();
    const { setInitialState } = useModel('@@initialState');

    const handleSubmit = async (values: API.LoginParams) => {
        setSubmitting(true);
        try {
            const response = await login(values);
            await setInitialState({ currentUser: response.data });
            message.success(t({ id: 'auth.login.success' }));

            const redirect = new URLSearchParams(location.search).get('redirect');
            const target = redirect?.startsWith('/') && !redirect.startsWith('//')
                ? redirect
                : '/';
            history.replace(target);
        } catch (error) {
            message.error(error instanceof Error ? error.message : t({ id: 'auth.login.failed' }));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <main className={styles.authPage}>
          <section className={styles.authPanel}>
            <header className={styles.intro}>
              <Link className={styles.brand} to="/">Photography Archive</Link>
              <p className={styles.kicker}>Private visual library</p>
              <h1>{t({ id: 'auth.login.title' })}</h1>
              <p className={styles.subtitle}>{t({ id: 'auth.login.subtitle' })}</p>
            </header>
            <Form<API.LoginParams>
                className={styles.form}
                layout="vertical"
                onFinish={handleSubmit}
                requiredMark={false}
            >
                <Form.Item
                    name="userAccount"
                    label={t({ id: 'auth.account' })}
                    rules={[
                        { required: true, message: t({ id: 'auth.account.required' }) },
                        { min: 4, max: 32, message: t({ id: 'auth.account.length' }) },
                    ]}
                >
                    <Input size="large" autoComplete="username" placeholder={t({ id: 'auth.account.placeholder' })} />
                </Form.Item>

                <Form.Item
                    name="userPassword"
                    label={t({ id: 'auth.password' })}
                    rules={[
                        { required: true, message: t({ id: 'auth.password.required' }) },
                        { min: 8, max: 72, message: t({ id: 'auth.password.length' }) },
                    ]}
                >
                    <Input.Password size="large" autoComplete="current-password" placeholder={t({ id: 'auth.password.placeholder' })} />
                </Form.Item>

                <Button className={styles.submit} size="large" type="primary" htmlType="submit" loading={submitting} block>
                    {t({ id: 'common.action.login' })}
                </Button>
            </Form>
            <div className={styles.switchPage}>
                {t({ id: 'auth.login.noAccount' })}<Link to="/user/register">{t({ id: 'auth.login.goRegister' })}</Link>
            </div>
          </section>
        </main>
    );
}

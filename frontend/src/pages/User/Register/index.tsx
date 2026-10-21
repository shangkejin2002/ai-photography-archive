import { Button, Form, Input, message } from 'antd';
import { useState } from 'react';
import { history, Link, useIntl } from 'umi';
import { register } from '@/services/user';
import styles from '../auth.less';

export default function RegisterPage() {
    const [submitting, setSubmitting] = useState(false);
    const { formatMessage: t } = useIntl();

    const handleSubmit = async (values: API.RegisterParams) => {
        setSubmitting(true);
        try {
            await register(values);
            message.success(t({ id: 'auth.register.success' }));
            history.replace('/user/login');
        } catch (error) {
            message.error(error instanceof Error ? error.message : t({ id: 'auth.register.failed' }));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <main className={styles.authPage}>
          <section className={`${styles.authPanel} ${styles.registerPanel}`}>
            <header className={styles.intro}>
              <Link className={styles.brand} to="/">Photography Archive</Link>
              <p className={styles.kicker}>Create your archive</p>
              <h1>{t({ id: 'auth.register.title' })}</h1>
              <p className={styles.subtitle}>{t({ id: 'auth.register.subtitle' })}</p>
            </header>
            <Form<API.RegisterParams>
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
                    <Input.Password size="large" autoComplete="new-password" placeholder={t({ id: 'auth.password.placeholder' })} />
                </Form.Item>

                <Form.Item
                    name="checkPassword"
                    label={t({ id: 'auth.password.confirm' })}
                    dependencies={['userPassword']}
                    rules={[
                        { required: true, message: t({ id: 'auth.password.confirmRequired' }) },
                        ({ getFieldValue }) => ({
                            validator(_, value) {
                                if (!value || value === getFieldValue('userPassword')) {
                                    return Promise.resolve();
                                }
                                return Promise.reject(new Error(t({ id: 'auth.password.mismatch' })));
                            },
                        }),
                    ]}
                >
                    <Input.Password size="large" autoComplete="new-password" placeholder={t({ id: 'auth.password.confirmPlaceholder' })} />
                </Form.Item>

                <Button className={styles.submit} size="large" type="primary" htmlType="submit" loading={submitting} block>
                    {t({ id: 'common.action.register' })}
                </Button>
            </Form>
            <div className={styles.switchPage}>
                {t({ id: 'auth.register.hasAccount' })}<Link to="/user/login">{t({ id: 'auth.register.goLogin' })}</Link>
            </div>
          </section>
        </main>
    );
}

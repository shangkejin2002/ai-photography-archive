import { Button, Form, Input, message, Popconfirm, Space, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useIntl } from 'umi';
import { deleteUser, searchUsers } from '@/services/user';

type SearchValues = {
    username?: string;
};

export default function AdminUserPage() {
    const [form] = Form.useForm<SearchValues>();
    const [users, setUsers] = useState<API.User[]>([]);
    const [loading, setLoading] = useState(false);
    const [deletingId, setDeletingId] = useState<number>();
    const { formatMessage: t } = useIntl();

    const loadUsers = useCallback(async (username?: string) => {
        setLoading(true);
        try {
            const response = await searchUsers(username?.trim() || undefined);
            setUsers(response.data ?? []);
        } catch (error) {
            message.error(error instanceof Error ? error.message : t({ id: 'admin.load.failed' }));
        } finally {
            setLoading(false);
        }
    }, [t]);

    useEffect(() => {
        void loadUsers();
    }, [loadUsers]);

    const handleDelete = async (id: number) => {
        setDeletingId(id);
        try {
            await deleteUser(id);
            message.success(t({ id: 'admin.delete.success' }));
            await loadUsers(form.getFieldValue('username'));
        } catch (error) {
            message.error(error instanceof Error ? error.message : t({ id: 'admin.delete.failed' }));
        } finally {
            setDeletingId(undefined);
        }
    };

    const columns: TableColumnsType<API.User> = [
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: t({ id: 'admin.username' }), dataIndex: 'username', render: (value) => value || '-' },
        { title: t({ id: 'admin.account' }), dataIndex: 'userAccount' },
        {
            title: t({ id: 'admin.role' }),
            dataIndex: 'userRole',
            render: (role: number) => role === 1
                ? <Tag color="red">{t({ id: 'admin.role.admin' })}</Tag>
                : <Tag>{t({ id: 'admin.role.user' })}</Tag>,
        },
        {
            title: t({ id: 'admin.status' }),
            dataIndex: 'userStatus',
            render: (status: number) => status === 0
                ? <Tag color="green">{t({ id: 'admin.status.active' })}</Tag>
                : <Tag color="orange">{t({ id: 'admin.status.inactive' })}</Tag>,
        },
        {
            title: t({ id: 'admin.action' }),
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Popconfirm
                    title={t({ id: 'admin.delete.title' })}
                    description={t({ id: 'admin.delete.description' })}
                    okText={t({ id: 'common.action.delete' })}
                    cancelText={t({ id: 'common.action.cancel' })}
                    okButtonProps={{ danger: true }}
                    onConfirm={() => handleDelete(record.id)}
                >
                    <Button type="link" danger loading={deletingId === record.id}>
                        {t({ id: 'common.action.delete' })}
                    </Button>
                </Popconfirm>
            ),
        },
    ];

    return (
        <div>
            <h1>{t({ id: 'admin.title' })}</h1>
            <Form<SearchValues>
                form={form}
                layout="inline"
                onFinish={({ username }) => loadUsers(username)}
                style={{ marginBottom: 24 }}
            >
                <Form.Item name="username" label={t({ id: 'admin.username' })}>
                    <Input allowClear placeholder={t({ id: 'admin.search.placeholder' })} />
                </Form.Item>
                <Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={loading}>
                            {t({ id: 'common.action.search' })}
                        </Button>
                        <Button
                            onClick={() => {
                                form.resetFields();
                                void loadUsers();
                            }}
                        >
                            {t({ id: 'common.action.reset' })}
                        </Button>
                    </Space>
                </Form.Item>
            </Form>
            <Table<API.User>
                rowKey="id"
                columns={columns}
                dataSource={users}
                loading={loading}
                scroll={{ x: 900 }}
                pagination={{ pageSize: 10, showSizeChanger: true }}
            />
        </div>
    );
}

import { useIntl } from 'umi';

export default function NotFoundPage() {
    const { formatMessage: t } = useIntl();
    return (
        <div>
            <h1>404</h1>
            <p>{t({ id: 'common.page.notFound' })}</p>
        </div>
    );
}

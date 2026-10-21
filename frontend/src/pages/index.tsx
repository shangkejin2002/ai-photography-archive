import { useIntl } from 'umi';

export default function HomePage() {
    const { formatMessage } = useIntl();
    return (
        <main>
            <h1>{formatMessage({ id: 'welcome.title' })}</h1>
            <p>{formatMessage({ id: 'welcome.started' })}</p>
        </main>
    );
}

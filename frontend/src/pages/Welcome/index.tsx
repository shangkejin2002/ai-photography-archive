import { useIntl } from 'umi';

export default function WelcomePage() {
    const { formatMessage } = useIntl();
    return (
        <div>
            <h1>{formatMessage({ id: 'welcome.title' })}</h1>
            <p>{formatMessage({ id: 'welcome.greeting' })}</p>
        </div>
    );
}

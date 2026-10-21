import { Alert, Button, Empty, Skeleton } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { history, useIntl } from 'umi';
import { listPortfolios } from '@/services/portfolio';
import styles from './index.less';

export default function PortfoliosPage() {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const [items, setItems] = useState<API.Portfolio[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [failedCovers, setFailedCovers] = useState<Set<number>>(new Set());

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      const response = await listPortfolios();
      setItems(response.data ?? []);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t({ id: 'portfolio.load.failed' }));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div><p>{t({ id: 'portfolio.kicker' })}</p><h1>Portfolios</h1></div>
        <Button type="primary" onClick={() => history.push('/ai-create')}>{t({ id: 'portfolio.createWithAi' })}</Button>
      </header>

      {loading ? (
        <div className={styles.loading}><Skeleton active /></div>
      ) : loadError ? (
        <Alert type="error" showIcon message={t({ id: 'portfolio.load.failed' })} description={loadError} action={<Button onClick={() => void load()}>{t({ id: 'common.action.retry' })}</Button>} />
      ) : items.length === 0 ? (
        <div className={styles.empty}><Empty description={t({ id: 'portfolio.empty' })}><Button type="primary" onClick={() => history.push('/ai-create')}>{t({ id: 'portfolio.createFirst' })}</Button></Empty></div>
      ) : (
        <div className={styles.grid}>{items.map((portfolio) => (
          <article
            className={styles.card}
            key={portfolio.id}
            role="button"
            tabIndex={0}
            onClick={() => history.push(`/portfolios/${portfolio.id}`)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') history.push(`/portfolios/${portfolio.id}`);
            }}
          >
            {portfolio.coverThumbnailUrl && !failedCovers.has(portfolio.id) ? (
              <img src={portfolio.coverThumbnailUrl} alt={portfolio.title} onError={() => setFailedCovers((current) => new Set(current).add(portfolio.id))} />
            ) : <div className={styles.placeholder}>{t({ id: 'portfolio.coverUnavailable' })}</div>}
            <div className={styles.copy}>
              <h2>{portfolio.title}</h2>
              <p>{portfolio.subtitle || t({ id: 'portfolio.defaultSubtitle' })}</p>
              <div className={styles.meta}><span>{t({ id: 'portfolio.photoCount' }, { count: portfolio.photos.length })}</span><span>{intl.formatDate(new Date(portfolio.updateTime))}</span></div>
            </div>
          </article>
        ))}</div>
      )}
    </div>
  );
}

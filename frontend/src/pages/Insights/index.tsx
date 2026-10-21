import { Button, Empty } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { history, useIntl } from 'umi';
import { getShootingInsights } from '@/services/insights';
import { localizedPhotoTagName } from '@/i18n/photo';
import styles from './index.less';

const DONUT_COLORS = ['#e2e1dc', '#b8b9b4', '#858783', '#595b58', '#373937'];
const LINE_WIDTH = 460;
const LINE_BASELINE = 114;

function ShootingLineChart({ data }: { data: API.MonthlyCount[] }) {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const geometry = useMemo(() => {
    if (!data.length) return { points: [] as { x: number; y: number }[], line: '', area: '', max: 0 };
    const max = Math.max(1, ...data.map((item) => item.count));
    const points = data.map((item, index) => ({
      x: 20 + index * ((LINE_WIDTH - 40) / Math.max(1, data.length - 1)),
      y: LINE_BASELINE - (item.count / max) * 79,
    }));
    const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ');
    const area = `${line} L ${points.at(-1)?.x} ${LINE_BASELINE} L ${points[0].x} ${LINE_BASELINE} Z`;
    return { points, line, area, max };
  }, [data]);

  if (!data.length) return <div className={styles.chartUnavailable}>{t({ id: 'insights.dateUnavailable' })}</div>;
  return (
    <svg className={styles.lineChart} viewBox={`0 0 ${LINE_WIDTH} 145`} role="img" aria-label={t({ id: 'insights.monthlyAria' })}>
      <defs>
        <linearGradient id="insights-line-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#deddd7" stopOpacity=".22" /><stop offset="1" stopColor="#deddd7" stopOpacity="0" /></linearGradient>
        <filter id="insights-line-shadow"><feDropShadow dx="0" dy="4" stdDeviation="4" floodOpacity=".34" /></filter>
      </defs>
      {[35, 74, 113].map((y) => <line className={styles.gridLine} key={y} x1="20" x2="440" y1={y} y2={y} />)}
      <path d={geometry.area} fill="url(#insights-line-fill)" />
      <path className={styles.linePath} d={geometry.line} filter="url(#insights-line-shadow)" />
      {geometry.points.map((point, index) => <circle key={data[index].month} className={styles.linePoint} cx={point.x} cy={point.y} r="3"><title>{data[index].month}：{t({ id: 'portfolio.photoCount' }, { count: data[index].count })}</title></circle>)}
      {data.map((item, index) => index % 2 === 0 || index === data.length - 1 ? <text key={item.month} x={geometry.points[index].x} y="139" textAnchor={index === 0 ? 'start' : index === data.length - 1 ? 'end' : 'middle'}>{intl.formatDate(new Date(`${item.month}-01T00:00:00Z`), { month: 'short', timeZone: 'UTC' })}</text> : null)}
    </svg>
  );
}

function SubjectDonut({ items }: { items: API.InsightItem[] }) {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const tagLabel = (value: string) => localizedPhotoTagName(value, intl);
  const radius = 45;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <div className={styles.donutLayout}>
      <div className={styles.donut}>
        <svg viewBox="0 0 120 120" role="img" aria-label={t({ id: 'insights.themeAria' })}>
          <circle className={styles.donutTrack} cx="60" cy="60" r={radius} />
          {items.map((item, index) => {
            const length = circumference * item.percentage / 100;
            const dashOffset = -offset;
            offset += length;
            return <circle key={item.label} className={styles.donutSegment} cx="60" cy="60" r={radius} stroke={DONUT_COLORS[index % DONUT_COLORS.length]} strokeDasharray={`${Math.max(0, length - 4)} ${circumference}`} strokeDashoffset={dashOffset}><title>{tagLabel(item.label)}：{item.percentage}%</title></circle>;
          })}
        </svg>
        <div className={styles.donutCenter}><strong>{items.length}</strong><span>{t({ id: 'insights.theme' })}</span></div>
      </div>
      <div className={styles.legend}>{items.length ? items.slice(0, 4).map((item, index) => <div key={item.label}><i style={{ background: DONUT_COLORS[index] }} /><span>{tagLabel(item.label)}</span><b>{item.percentage}%</b></div>) : <p>{t({ id: 'insights.themeEmpty' })}</p>}</div>
    </div>
  );
}

function AtmosphereRank({ insights }: { insights: API.ShootingInsights }) {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const tagLabel = (value: string) => localizedPhotoTagName(value, intl);
  const rows = [
    { label: t({ id: 'insights.dominantColor' }), item: insights.dominantColors?.[0] },
    { label: t({ id: 'insights.mood' }), item: insights.moods?.[0] },
    { label: t({ id: 'insights.lighting' }), item: insights.lighting?.[0] },
  ];
  return <div className={styles.ranks}>{rows.map(({ label, item }) => <div key={label}><div className={styles.rankHead}><span><em>{label}</em>{item ? tagLabel(item.label) : t({ id: 'insights.waitingIndex' })}</span><b>{item?.percentage ?? 0}%</b></div><div className={styles.rankTrack}><i style={{ width: `${item?.percentage ?? 0}%` }} /></div></div>)}</div>;
}

function LoadingView() {
  return <div className={`${styles.page} ${styles.loadingPage}`}><div className={styles.loadingHeading} /><div className={styles.dashboard}>{Array.from({ length: 5 }).map((_, index) => <div className={styles.loadingPanel} key={index} />)}</div></div>;
}

export default function InsightsPage() {
  const { formatMessage: t } = useIntl();
  const [insights, setInsights] = useState<API.ShootingInsights>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const response = await getShootingInsights();
      setInsights(response.data);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t({ id: 'insights.load.failed' }));
    } finally { setLoading(false); }
  }, [t]);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <LoadingView />;
  if (error) return <div className={styles.centerState}><div><span>{t({ id: 'insights.unavailable' })}</span><h1>{t({ id: 'insights.archiveUnavailable' })}</h1><p>{error}</p><Button onClick={() => void load()}>{t({ id: 'insights.reload' })}</Button></div></div>;
  if (!insights?.totalPhotos) return <div className={styles.centerState}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t({ id: 'insights.empty' })}><Button type="primary" onClick={() => history.push('/library')}>{t({ id: 'insights.goLibrary' })}</Button></Empty></div>;

  const focalMaximum = Math.max(1, ...insights.focalRanges.map((item) => item.count));
  const dominantFocal = insights.focalRanges.filter((item) => item.count > 0).sort((left, right) => right.count - left.count)[0];
  const year = insights.monthlyCounts.at(-1)?.month.split('-')[0] ?? t({ id: 'insights.archive' });
  const unnamedGps = Math.max(0, insights.gpsPhotos - insights.namedLocationPhotos);
  const observations = insights.observationDetails?.length ? insights.observationDetails : [insights.observation];

  return (
    <div className={styles.page}>
      <header className={styles.heading}>
        <div><p>{t({ id: 'insights.kicker' })}</p><h1>{t({ id: 'insights.title' })}</h1></div>
        <div className={styles.statline}><div><strong>{insights.totalPhotos}</strong><span>{t({ id: 'insights.total' })}</span></div><div><strong>{insights.exifCoverage}%</strong><span>{t({ id: 'insights.exifCoverage' })}</span></div><div><strong>{insights.aiCoverage}%</strong><span>{t({ id: 'insights.aiCoverage' })}</span></div></div>
      </header>

      <section className={styles.dashboard} aria-label={t({ id: 'insights.dashboardAria' })}>
        <article className={`${styles.panel} ${styles.rhythm}`}>
          <div className={styles.panelTitle}><div><h2>{t({ id: 'insights.rhythm' })}</h2><p>{t({ id: 'insights.rhythmDescription' })}</p></div><strong>{year}</strong></div>
          <ShootingLineChart data={insights.monthlyCounts} />
        </article>

        <article className={`${styles.panel} ${styles.visualLanguage}`}>
          <div className={styles.panelTitle}><div><h2>{t({ id: 'insights.visualTheme' })}</h2><p>{t({ id: 'insights.visualThemeDescription' })}</p></div></div>
          <SubjectDonut items={insights.subjects} />
        </article>

        <article className={`${styles.panel} ${styles.observation}`}>
          <div className={styles.observationHead}><span>{t({ id: 'insights.observation' })}</span><i>{t({ id: 'insights.observationSource' }, { count: insights.totalPhotos })}</i></div>
          <div className={styles.observationCopy}>{observations.map((item, index) => <p key={`${index}-${item}`}>{item}</p>)}</div>
          <div className={styles.observationMeta}>
            <div><span>{t({ id: 'insights.locations' })}</span>{insights.locations.length ? insights.locations.slice(0, 3).map((item) => <i key={item.label}>{item.label} · {item.count}</i>) : <i>{t({ id: 'insights.locationsEmpty' })}</i>}</div>
            <div className={styles.miniCoverage}><span>{t({ id: 'insights.dataCoverage' })}</span><b>EXIF {insights.exifCoverage}%</b><b>GPS {insights.gpsCoverage}%</b><b>{t({ id: 'insights.aiCoverage' })} {insights.aiCoverage}%</b></div>
            {unnamedGps > 0 && <small>{t({ id: 'insights.unnamedGps' }, { count: unnamedGps })}</small>}
          </div>
          <footer><span>{t({ id: 'insights.generatedFrom' })}</span><span>{t({ id: 'insights.noAiCall' })}</span></footer>
        </article>

        <article className={`${styles.panel} ${styles.focal}`}>
          <div className={styles.panelTitle}><div><h2>{t({ id: 'insights.focal' })}</h2><p>{t({ id: 'insights.focalDescription' })}</p></div><strong>{dominantFocal?.label ?? '—'}</strong></div>
          <div className={styles.bars}>{insights.focalRanges.map((item) => <div className={styles.barItem} key={item.label}><div className={styles.barTrack}><i style={{ height: `${item.count ? Math.max(12, item.count / focalMaximum * 100) : 0}%` }}><span>{item.count || ''}</span></i></div><small>{item.label.replace('mm', '')}</small></div>)}</div>
        </article>

        <article className={`${styles.panel} ${styles.atmosphere}`}>
          <div className={styles.panelTitle}><div><h2>{t({ id: 'insights.atmosphere' })}</h2><p>{t({ id: 'insights.atmosphereDescription' })}</p></div></div>
          <AtmosphereRank insights={insights} />
        </article>
      </section>
    </div>
  );
}

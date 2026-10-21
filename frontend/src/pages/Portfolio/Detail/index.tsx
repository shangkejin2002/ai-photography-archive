import { Button, Input, message, Popconfirm, Skeleton } from 'antd';
import { useEffect, useState } from 'react';
import { history, useIntl, useParams } from 'umi';
import {
  deletePortfolio, disablePortfolioSharing, enablePortfolioSharing,
  exportPortfolioPdf, getPortfolio, updatePortfolio,
} from '@/services/portfolio';
import styles from './index.less';
import { localizedPhotoDescription } from '@/i18n/photo';
import { localizeJapanesePhotos } from '@/services/photo';

export default function PortfolioDetailPage() {
  const { portfolioId } = useParams<{ portfolioId: string }>();
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const [portfolio, setPortfolio] = useState<API.Portfolio>();
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const id = Number(portfolioId);

  useEffect(() => {
    getPortfolio(id).then((response) => {
      setPortfolio(response.data);
      setTitle(response.data.title);
      setSubtitle(response.data.subtitle ?? '');
      const missingIds = response.data.photos
        .filter((photo) => photo.aiDescription && !photo.localizedAiDescription)
        .map((photo) => photo.id);
      if (intl.locale.startsWith('ja') && missingIds.length) {
        void localizeJapanesePhotos(missingIds).then((localized) => {
          const byId = new Map(localized.data.map((photo) => [photo.id, photo]));
          setPortfolio((current) => current ? {
            ...current,
            photos: current.photos.map((photo) => byId.get(photo.id) ?? photo),
          } : current);
        }).catch(() => undefined);
      }
    }).catch(() => history.replace('/portfolios')).finally(() => setLoading(false));
  }, [id, intl.locale]);

  const save = async () => {
    if (!portfolio) return;
    setSaving(true);
    try {
      const response = await updatePortfolio(portfolio.id, {
        title, subtitle, description: portfolio.description ?? undefined,
        coverPhotoId: portfolio.coverPhotoId ?? undefined,
        photoIds: portfolio.photos.map((photo) => photo.id),
      });
      setPortfolio(response.data);
      setEditing(false);
      message.success(t({ id: 'portfolio.update.success' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'portfolio.update.failed' }));
    } finally { setSaving(false); }
  };

  const remove = async () => {
    if (!portfolio) return;
    await deletePortfolio(portfolio.id);
    message.success(t({ id: 'portfolio.delete.success' }));
    history.replace('/portfolios');
  };

  const copyShareUrl = async (url: string) => {
    try { await navigator.clipboard.writeText(url); }
    catch {
      const input = document.createElement('textarea');
      input.value = url; input.style.position = 'fixed'; input.style.opacity = '0';
      document.body.appendChild(input); input.select(); document.execCommand('copy'); input.remove();
    }
  };

  const enableShare = async () => {
    if (!portfolio) return;
    setSharing(true);
    try {
      const response = await enablePortfolioSharing(portfolio.id);
      setPortfolio(response.data);
      if (response.data.shareUrl) await copyShareUrl(response.data.shareUrl);
      message.success(t({ id: 'portfolio.share.generated' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'portfolio.share.generateFailed' }));
    } finally { setSharing(false); }
  };

  const copyShare = async () => {
    if (!portfolio?.shareUrl) return;
    await copyShareUrl(portfolio.shareUrl);
    message.success(t({ id: 'portfolio.share.copied' }));
  };

  const disableShare = async () => {
    if (!portfolio) return;
    setSharing(true);
    try {
      const response = await disablePortfolioSharing(portfolio.id);
      setPortfolio(response.data);
      message.success(t({ id: 'portfolio.share.closed' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'portfolio.share.closeFailed' }));
    } finally { setSharing(false); }
  };

  const exportPdf = async () => {
    if (!portfolio) return;
    setExporting(true);
    try {
      if (intl.locale.startsWith('ja')) {
        const missingIds = portfolio.photos
          .filter((photo) => photo.aiDescription && !photo.localizedAiDescription)
          .map((photo) => photo.id);
        if (missingIds.length) await localizeJapanesePhotos(missingIds);
      }
      const blob = await exportPortfolioPdf(portfolio.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const safeTitle = portfolio.title.replace(/[\\/:*?"<>|]/g, '-').trim() || `portfolio-${portfolio.id}`;
      anchor.href = url; anchor.download = `${safeTitle}.pdf`;
      document.body.appendChild(anchor); anchor.click(); anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      message.success(t({ id: 'portfolio.pdf.success' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'portfolio.pdf.failed' }));
    } finally { setExporting(false); }
  };

  if (loading) return <div className={styles.loading}><Skeleton active /></div>;
  if (!portfolio) return null;

  return <div className={styles.page}>
    <div className={styles.toolbar}>
      <Button type="text" onClick={() => history.push('/portfolios')}>← Portfolios</Button>
      <div>
        <Button loading={exporting} onClick={() => void exportPdf()}>{t({ id: 'portfolio.pdf.export' })}</Button>
        {portfolio.shareEnabled ? <>
          <Button loading={sharing} onClick={() => void copyShare()}>{t({ id: 'portfolio.share.copy' })}</Button>
          <Popconfirm title={t({ id: 'portfolio.share.closeTitle' })} description={t({ id: 'portfolio.share.closeDescription' })} onConfirm={() => void disableShare()}>
            <Button>{t({ id: 'portfolio.share.close' })}</Button>
          </Popconfirm>
        </> : <Button loading={sharing} onClick={() => void enableShare()}>{t({ id: 'portfolio.share.generate' })}</Button>}
        <Button onClick={() => setEditing((value) => !value)}>{t({ id: 'portfolio.title.edit' })}</Button>
        <Button type="primary" onClick={() => history.push(`/portfolios/${portfolio.id}/story`)}>{t({ id: 'portfolio.story.play' })}</Button>
        <Popconfirm title={t({ id: 'portfolio.delete.title' })} description={t({ id: 'portfolio.delete.description' })} onConfirm={() => void remove()}>
          <Button danger>{t({ id: 'common.action.delete' })}</Button>
        </Popconfirm>
      </div>
    </div>

    {portfolio.shareEnabled && portfolio.shareUrl && <aside className={styles.sharePanel}>
      <div><strong>{t({ id: 'portfolio.share.activeTitle' })}</strong><span>{t({ id: 'portfolio.share.activeDescription' })}</span></div>
      <button type="button" onClick={() => void copyShare()}>{portfolio.shareUrl}</button>
    </aside>}

    <header className={styles.hero}>{editing ? <div className={styles.edit}>
      <Input value={title} onChange={(event) => setTitle(event.target.value)} />
      <Input.TextArea value={subtitle} autoSize onChange={(event) => setSubtitle(event.target.value)} />
      <Button type="primary" loading={saving} onClick={() => void save()}>{t({ id: 'portfolio.title.save' })}</Button>
    </div> : <>
      <p>Portfolio · {t({ id: 'portfolio.photographs' }, { count: portfolio.photos.length })}</p>
      <h1>{portfolio.title}</h1><h2>{portfolio.subtitle}</h2>
      {portfolio.sourcePrompt && <span>{t({ id: 'portfolio.createdFrom' }, { prompt: portfolio.sourcePrompt })}</span>}
    </>}</header>

    <main className={styles.sequence}>{portfolio.photos.map((photo, index) => <figure key={photo.id} className={index % 3 === 1 ? styles.narrow : ''}>
      <img src={photo.originalUrl} alt={photo.originalFilename} loading="lazy" onClick={() => history.push(`/photos/${photo.id}`)} />
      <figcaption><span>{String(index + 1).padStart(2, '0')}</span><strong>{photo.locationName || localizedPhotoDescription(photo, intl) || photo.originalFilename}</strong></figcaption>
    </figure>)}</main>
    <footer><Button size="large" type="primary" onClick={() => history.push(`/portfolios/${portfolio.id}/story`)}>{t({ id: 'portfolio.story.experience' })}</Button></footer>
  </div>;
}

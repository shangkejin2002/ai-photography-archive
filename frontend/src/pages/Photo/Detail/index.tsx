import { Button, Descriptions, Dropdown, Input, message, Popconfirm, Skeleton, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { history, useIntl, useParams } from 'umi';
import { analyzePhoto, deletePhoto, getPhoto, localizeJapanesePhotos, setPhotoFavorite, setPhotoLocation } from '@/services/photo';
import { localizedPhotoDescription, localizedPhotoTagName } from '@/i18n/photo';
import styles from './index.less';

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const formatCoordinates = (latitude?: number | null, longitude?: number | null) => {
  if (latitude === null || latitude === undefined || longitude === null || longitude === undefined) return undefined;
  const latitudeText = `${Math.abs(latitude).toFixed(6)}° ${latitude >= 0 ? 'N' : 'S'}`;
  const longitudeText = `${Math.abs(longitude).toFixed(6)}° ${longitude >= 0 ? 'E' : 'W'}`;
  return `${latitudeText}, ${longitudeText}`;
};

const analysisStatusKey = {
  PENDING: 'photo.status.pending',
  ANALYZING: 'photo.status.running',
  COMPLETED: 'photo.status.completed',
  FAILED: 'photo.status.failed',
} as const;

export default function PhotoDetailPage() {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const { photoId } = useParams<{ photoId: string }>();
  const [photo, setPhoto] = useState<API.Photo>();
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [locationDraft, setLocationDraft] = useState('');
  const [editingLocation, setEditingLocation] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    const id = Number(photoId);
    if (!Number.isFinite(id) || id <= 0) {
      message.error(t({ id: 'photo.invalidId' }));
      history.replace('/library');
      return;
    }
    setLoading(true);
    getPhoto(id)
      .then((response) => {
        setPhoto(response.data);
        setLocationDraft(response.data.locationName ?? '');
        if (intl.locale.startsWith('ja') && response.data.aiDescription && !response.data.localizedAiDescription) {
          void localizeJapanesePhotos([response.data.id])
            .then((localized) => localized.data[0] && setPhoto(localized.data[0]))
            .catch(() => undefined);
        }
      })
      .catch((error) => {
        message.error(error instanceof Error ? error.message : t({ id: 'photo.load.failed' }));
        history.replace('/library');
      })
      .finally(() => setLoading(false));
  }, [intl.locale, photoId, t]);

  useEffect(() => {
    if (!photo || !['PENDING', 'ANALYZING'].includes(photo.analysisStatus)) return undefined;
    const timer = window.setInterval(() => {
      void getPhoto(photo.id).then((response) => setPhoto(response.data)).catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [photo?.id, photo?.analysisStatus]);

  const handleFavorite = async () => {
    if (!photo) return;
    try {
      const response = await setPhotoFavorite(photo.id, !photo.favorite);
      setPhoto(response.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'photo.favorite.failed' }));
    }
  };

  const handleDelete = async () => {
    if (!photo) return;
    setDeleting(true);
    try {
      await deletePhoto(photo.id);
      message.success(t({ id: 'photo.delete.success' }));
      history.replace('/library');
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'photo.delete.failed' }));
    } finally {
      setDeleting(false);
    }
  };

  const handleAnalyze = async () => {
    if (!photo) return;
    setAnalyzing(true);
    try {
      const response = await analyzePhoto(photo.id);
      setPhoto(response.data);
      message.success(t({ id: 'photo.analysis.submitted' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'photo.analysis.submitFailed' }));
    } finally {
      setAnalyzing(false);
    }
  };

  const handleLocationSave = async () => {
    if (!photo) return;
    try {
      const response = await setPhotoLocation(photo.id, locationDraft);
      setPhoto(response.data);
      setEditingLocation(false);
      message.success(t({ id: 'photo.location.saved' }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'photo.location.failed' }));
    }
  };

  if (loading) {
    return <div className={styles.loading}><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }
  if (!photo) return null;

  const coordinates = formatCoordinates(photo.gpsLatitude, photo.gpsLongitude);
  const formatNumber = (value?: number | null, suffix = '') => value === null || value === undefined
    ? '—'
    : `${intl.formatNumber(Number(value), { maximumFractionDigits: 2 })}${suffix}`;
  const groupedTags = (photo.aiTags ?? []).reduce<Partial<Record<API.PhotoTag['type'], API.PhotoTag[]>>>((groups, tag) => {
    (groups[tag.type] ??= []).push(tag);
    return groups;
  }, {});
  const hasExif = photo.metadataStatus === 'EXTRACTED';
  const mapPoint = `${photo.gpsLatitude},${photo.gpsLongitude}`;
  const encodedMapPoint = encodeURIComponent(mapPoint);
  const mapMenuItems = [
    {
      key: 'apple',
      label: (
        <a
          href={`https://maps.apple.com/?ll=${encodedMapPoint}&q=Photo%20Location&z=17`}
          target="_blank"
          rel="noreferrer"
        >
          Apple Maps
        </a>
      ),
    },
    {
      key: 'google',
      label: (
        <a
          href={`https://www.google.com/maps/search/?api=1&query=${encodedMapPoint}`}
          target="_blank"
          rel="noreferrer"
        >
          Google Maps
        </a>
      ),
    },
    {
      key: 'openstreetmap',
      label: (
        <a
          href={`https://www.openstreetmap.org/?mlat=${photo.gpsLatitude}&mlon=${photo.gpsLongitude}#map=17/${photo.gpsLatitude}/${photo.gpsLongitude}`}
          target="_blank"
          rel="noreferrer"
        >
          OpenStreetMap
        </a>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.topbar}>
        <Button type="text" onClick={() => history.push('/library')}>← Library</Button>
        <div className={styles.topActions}>
          <Button onClick={() => void handleFavorite()}>{photo.favorite ? `♥ ${t({ id: 'photo.favorited' })}` : `♡ ${t({ id: 'photo.favorite' })}`}</Button>
          <Popconfirm
            title={t({ id: 'photo.delete.title' })}
            description={t({ id: 'photo.delete.description' })}
            okText={t({ id: 'common.action.delete' })}
            cancelText={t({ id: 'common.action.cancel' })}
            okButtonProps={{ danger: true, loading: deleting }}
            onConfirm={() => void handleDelete()}
          >
            <Button danger>{t({ id: 'common.action.delete' })}</Button>
          </Popconfirm>
        </div>
      </div>

      <div className={styles.detailGrid}>
        <section className={styles.imageStage}>
          {imageFailed ? <div className={styles.imageFallback}><strong>{t({ id: 'common.image.unavailable' })}</strong><span>{t({ id: 'photo.imageFallback' })}</span></div> : <img src={photo.originalUrl} alt={photo.originalFilename} onError={() => setImageFailed(true)} />}
        </section>

        <aside className={styles.information}>
          <p className={styles.kicker}>{t({ id: 'photo.detail' })}</p>
          <h1>{photo.locationName || photo.originalFilename}</h1>
          <p className={styles.filename}>{photo.originalFilename}</p>

          <div className={styles.statusRow}>
            <Tag bordered={false}>{t({ id: analysisStatusKey[photo.analysisStatus] })}</Tag>
            {photo.favorite && <Tag bordered={false}>{t({ id: 'photo.favorite' })}</Tag>}
          </div>

          <section className={styles.infoSection}>
            <h2>{t({ id: 'photo.file.title' })}</h2>
            <Descriptions column={1} colon={false} size="small">
              <Descriptions.Item label={t({ id: 'photo.file.dimensions' })}>{photo.width} × {photo.height}</Descriptions.Item>
              <Descriptions.Item label={t({ id: 'photo.file.size' })}>{formatBytes(photo.fileSize)}</Descriptions.Item>
              <Descriptions.Item label={t({ id: 'photo.file.format' })}>{photo.contentType}</Descriptions.Item>
              <Descriptions.Item label={t({ id: 'photo.file.uploaded' })}>{intl.formatDate(new Date(photo.createTime), { dateStyle: 'medium', timeStyle: 'short' })}</Descriptions.Item>
            </Descriptions>
          </section>

          <section className={styles.infoSection}>
            <h2>{t({ id: 'photo.exif.title' })}</h2>
            {hasExif ? (
              <>
                <div className={styles.exposureGrid}>
                  <div><span>{t({ id: 'photo.exif.focal' })}</span><strong>{formatNumber(photo.focalLength, 'mm')}</strong></div>
                  <div><span>{t({ id: 'photo.exif.aperture' })}</span><strong>{photo.aperture ? `f/${intl.formatNumber(Number(photo.aperture))}` : '—'}</strong></div>
                  <div><span>{t({ id: 'photo.exif.shutter' })}</span><strong>{photo.shutterSpeed || '—'}</strong></div>
                  <div><span>ISO</span><strong>{photo.isoValue ?? '—'}</strong></div>
                </div>
                <Descriptions column={1} colon={false} size="small">
                  <Descriptions.Item label={t({ id: 'photo.exif.camera' })}>{[photo.cameraMake, photo.cameraModel].filter(Boolean).join(' ') || '—'}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.lens' })}>{photo.lensModel || '—'}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.equivalent' })}>{formatNumber(photo.focalLength35mm, 'mm')}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.takenAt' })}>{photo.takenAt ? intl.formatDate(new Date(photo.takenAt), { dateStyle: 'medium', timeStyle: 'short' }) : '—'}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.bias' })}>{photo.exposureBias === null || photo.exposureBias === undefined ? '—' : `${photo.exposureBias > 0 ? '+' : ''}${photo.exposureBias} EV`}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.metering' })}>{photo.meteringMode || '—'}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.whiteBalance' })}>{photo.whiteBalance || '—'}</Descriptions.Item>
                  <Descriptions.Item label={t({ id: 'photo.exif.flash' })}>{photo.flashStatus || '—'}</Descriptions.Item>
                </Descriptions>
              </>
            ) : (
              <p className={styles.muted}>
                {photo.metadataStatus === 'FAILED'
                  ? (intl.locale.startsWith('ja') ? t({ id: 'photo.metadata.failed' }) : photo.metadataError || t({ id: 'photo.metadata.failed' }))
                  : t({ id: 'photo.metadata.missing' })}
              </p>
            )}
          </section>

          <section className={styles.infoSection}>
            <h2>{t({ id: 'photo.location.title' })}</h2>
            {coordinates ? (
              <div className={styles.locationBlock}>
                <strong>{photo.locationName || t({ id: 'photo.location.gpsRecorded' })}</strong>
                <span>{coordinates}</span>
                <Dropdown menu={{ items: mapMenuItems }} placement="bottomLeft" trigger={['click']}>
                  <Button className={styles.mapButton}>{t({ id: 'photo.location.viewMap' })} <span aria-hidden="true">⌄</span></Button>
                </Dropdown>
                {editingLocation ? <div className={styles.locationEditor}><Input value={locationDraft} maxLength={512} placeholder={t({ id: 'photo.location.placeholder' })} onChange={(event) => setLocationDraft(event.target.value)} /><Button onClick={() => void handleLocationSave()}>{t({ id: 'photo.location.save' })}</Button></div> : <Button type="text" className={styles.locationEditButton} onClick={() => setEditingLocation(true)}>{t({ id: 'photo.location.edit' })}</Button>}
              </div>
            ) : (
              <div className={styles.locationBlock}>
                <strong>{photo.locationName || t({ id: 'photo.location.notRecorded' })}</strong>
                <span>{t({ id: 'photo.location.noGps' })}</span>
                {editingLocation ? <div className={styles.locationEditor}><Input value={locationDraft} maxLength={512} placeholder={t({ id: 'photo.location.placeholder' })} onChange={(event) => setLocationDraft(event.target.value)} /><Button onClick={() => void handleLocationSave()}>{t({ id: 'photo.location.save' })}</Button></div> : <Button type="text" className={styles.locationEditButton} onClick={() => setEditingLocation(true)}>{t({ id: 'photo.location.add' })}</Button>}
              </div>
            )}
          </section>

          <section className={styles.infoSection}>
            <h2>{t({ id: 'photo.ai.title' })}</h2>
            {photo.analysisStatus === 'COMPLETED' ? (
              <div className={styles.aiUnderstanding}>
                <p className={styles.aiDescription}>{localizedPhotoDescription(photo, intl) || t({ id: 'photo.analysis.completed' })}</p>
                {Object.entries(groupedTags).map(([type, tags]) => (
                  <div className={styles.tagGroup} key={type}>
                    <span>{t({ id: `photo.tag.${type}` })}</span>
                    <div>{tags?.map((tag) => <Tag bordered={false} key={`${type}-${tag.name}`}>{localizedPhotoTagName(tag.name, intl)}</Tag>)}</div>
                  </div>
                ))}
                <p className={styles.analysisMeta}>
                  {photo.analysisModel} · {photo.analysisVersion}
                  {photo.analyzedAt ? ` · ${intl.formatDate(new Date(photo.analyzedAt), { dateStyle: 'medium', timeStyle: 'short' })}` : ''}
                </p>
                {photo.analysisVersion !== 'stage5-v2' && (
                  <Button loading={analyzing} onClick={() => void handleAnalyze()}>{t({ id: 'photo.ai.upgrade' })}</Button>
                )}
              </div>
            ) : photo.analysisStatus === 'FAILED' ? (
              <div className={styles.analysisNotice}>
                <p>{intl.locale.startsWith('ja') ? t({ id: 'photo.analysis.failed' }) : photo.analysisError || t({ id: 'photo.analysis.failed' })}</p>
                <Button loading={analyzing} onClick={() => void handleAnalyze()}>{t({ id: 'photo.ai.retry' })}</Button>
              </div>
            ) : (
              <div className={styles.analysisNotice}>
                <p>{photo.analysisStatus === 'ANALYZING' ? t({ id: 'photo.analysis.running' }) : t({ id: 'photo.analysis.queued' })}</p>
                <span className={styles.processingDot} aria-hidden="true" />
              </div>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}

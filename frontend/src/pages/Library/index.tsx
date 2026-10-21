import { Alert, Button, Empty, Input, message, Progress, Segmented, Skeleton } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useIntl } from 'umi';
import { analyzePendingPhotos, listPhotos, setPhotoFavorite, uploadPhoto } from '@/services/photo';
import styles from './index.less';

type LibraryFilter = 'all' | 'favorites';
type UploadStatus = 'waiting' | 'uploading' | 'processing' | 'completed' | 'failed';

type UploadItem = {
  id: string;
  file: File;
  status: UploadStatus;
  progress: number;
  error?: string;
};

const MAX_FILE_SIZE = 20 * 1024 * 1024;
const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png']);

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const validateFile = (file: File, t: (descriptor: { id: string }) => string) => {
  if (file.size === 0) return t({ id: 'library.upload.emptyFile' });
  if (file.size > MAX_FILE_SIZE) return t({ id: 'library.upload.tooLarge' });
  const extensionLooksValid = /\.(jpe?g|png)$/i.test(file.name);
  if (!ACCEPTED_TYPES.has(file.type) && !(file.type === '' && extensionLooksValid)) {
    return t({ id: 'library.upload.unsupported' });
  }
  return undefined;
};

export default function LibraryPage() {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const [photos, setPhotos] = useState<API.Photo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [uploading, setUploading] = useState(false);
  const [submittingAnalysis, setSubmittingAnalysis] = useState(false);
  const [uploadItems, setUploadItems] = useState<UploadItem[]>([]);
  const [dragging, setDragging] = useState(false);
  const [filter, setFilter] = useState<LibraryFilter>('all');
  const [search, setSearch] = useState('');
  const [failedThumbnails, setFailedThumbnails] = useState<Set<number>>(new Set());
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragDepthRef = useRef(0);

  const loadPhotos = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      const response = await listPhotos(filter === 'favorites' ? true : undefined);
      setPhotos(response.data ?? []);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t({ id: 'library.load.failed' }));
    } finally {
      setLoading(false);
    }
  }, [filter, t]);

  useEffect(() => {
    void loadPhotos();
  }, [loadPhotos]);

  const visiblePhotos = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return photos;
    return photos.filter((photo) =>
      [
        photo.originalFilename,
        photo.locationName,
        photo.cameraModel,
        photo.aiDescription,
        ...(photo.aiTags ?? []).map((tag) => tag.name),
      ]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(keyword)),
    );
  }, [photos, search]);

  const updateUploadItem = (id: string, values: Partial<UploadItem>) => {
    setUploadItems((items) => items.map((item) => item.id === id ? { ...item, ...values } : item));
  };

  const uploadOne = async (item: UploadItem) => {
    updateUploadItem(item.id, { status: 'uploading', progress: 0, error: undefined });
    try {
      await uploadPhoto(item.file, (progress) => {
        updateUploadItem(item.id, {
          status: progress >= 100 ? 'processing' : 'uploading',
          progress,
        });
      });
      updateUploadItem(item.id, { status: 'completed', progress: 100 });
      return true;
    } catch (error) {
      updateUploadItem(item.id, {
        status: 'failed',
        error: error instanceof Error ? error.message : t({ id: 'library.upload.failed' }),
      });
      return false;
    }
  };

  const uploadBatch = async (items: UploadItem[]) => {
    const validItems = items.filter((item) => item.status === 'waiting');
    if (!validItems.length) return;
    setUploading(true);
    let successCount = 0;
    try {
      for (const item of validItems) {
        if (await uploadOne(item)) successCount += 1;
      }
      if (successCount > 0) {
        message.success(t({ id: 'library.upload.batchSuccess' }, { count: successCount }));
        setFilter('all');
        await loadPhotos();
      }
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleFiles = (files: FileList | File[]) => {
    if (uploading) {
      message.info(t({ id: 'library.upload.waitCurrent' }));
      return;
    }
    const selected = Array.from(files);
    if (!selected.length) return;
    const stamp = Date.now();
    const items: UploadItem[] = selected.map((file, index) => {
      const error = validateFile(file, t);
      return {
        id: `${stamp}-${index}-${file.name}`,
        file,
        status: error ? 'failed' : 'waiting',
        progress: 0,
        error,
      };
    });
    setUploadItems((current) => [...items, ...current].slice(0, 30));
    void uploadBatch(items);
  };

  const retryUpload = async (item: UploadItem) => {
    if (uploading || validateFile(item.file, t)) return;
    setUploading(true);
    try {
      if (await uploadOne(item)) {
        message.success(t({ id: 'library.upload.itemSuccess' }, { name: item.file.name }));
        setFilter('all');
        await loadPhotos();
      }
    } finally {
      setUploading(false);
    }
  };

  const handleFavorite = async (event: React.MouseEvent, photo: API.Photo) => {
    event.stopPropagation();
    try {
      const response = await setPhotoFavorite(photo.id, !photo.favorite);
      setPhotos((items) => filter === 'favorites' && !response.data.favorite
        ? items.filter((item) => item.id !== photo.id)
        : items.map((item) => item.id === photo.id ? response.data : item));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'library.favorite.failed' }));
    }
  };

  const handleDragEnter = (event: React.DragEvent) => {
    event.preventDefault();
    dragDepthRef.current += 1;
    if (Array.from(event.dataTransfer.types).includes('Files')) setDragging(true);
  };

  const handleDragLeave = (event: React.DragEvent) => {
    event.preventDefault();
    dragDepthRef.current -= 1;
    if (dragDepthRef.current <= 0) {
      dragDepthRef.current = 0;
      setDragging(false);
    }
  };

  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    dragDepthRef.current = 0;
    setDragging(false);
    handleFiles(event.dataTransfer.files);
  };

  const openFilePicker = () => fileInputRef.current?.click();

  const handleAnalyzeArchive = async () => {
    setSubmittingAnalysis(true);
    try {
      const response = await analyzePendingPhotos();
      message.success(response.data > 0
        ? t({ id: 'library.analysis.submitted' }, { count: response.data })
        : t({ id: 'library.analysis.none' }));
      window.setTimeout(() => void loadPhotos(), 1200);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'library.analysis.failed' }));
    } finally {
      setSubmittingAnalysis(false);
    }
  };

  return (
    <div
      className={styles.page}
      onDragEnter={handleDragEnter}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {dragging && (
        <div className={styles.dropOverlay} aria-hidden="true">
          <div><strong>{t({ id: 'library.drop.title' })}</strong><span>{t({ id: 'library.drop.help' })}</span></div>
        </div>
      )}

      <section className={styles.hero}>
        <p className={styles.kicker}>{t({ id: 'library.kicker' })}</p>
        <h1>{t({ id: 'library.hero' }).split('\n').map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1>
        <p className={styles.subtitle}>{t({ id: 'library.subtitle' })}</p>
      </section>

      <section className={styles.toolbar} aria-label={t({ id: 'library.toolbar.aria' })}>
        <Input.Search
          className={styles.search}
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t({ id: 'library.search.placeholder' })}
        />
        <div className={styles.actions}>
          <Segmented<LibraryFilter>
            value={filter}
            onChange={setFilter}
            options={[
              { label: t({ id: 'library.filter.all' }), value: 'all' },
              { label: t({ id: 'library.filter.favorites' }), value: 'favorites' },
            ]}
          />
          <input
            ref={fileInputRef}
            className={styles.fileInput}
            type="file"
            accept="image/jpeg,image/png,.jpg,.jpeg,.png"
            multiple
            onChange={(event) => event.target.files && handleFiles(event.target.files)}
          />
          <Button loading={submittingAnalysis} onClick={() => void handleAnalyzeArchive()}>
            {t({ id: 'library.upload.analyze' })}
          </Button>
          <Button type="primary" loading={uploading} onClick={openFilePicker}>
            {t({ id: 'library.upload.select' })}
          </Button>
        </div>
      </section>

      {uploadItems.length > 0 && (
        <section className={styles.uploadQueue} aria-label={t({ id: 'library.upload.queueAria' })}>
          <div className={styles.queueHeader}>
            <div><span>{t({ id: 'library.upload.queue' })}</span><strong>{uploadItems.filter((item) => item.status === 'completed').length} / {uploadItems.length}</strong></div>
            {!uploading && <button type="button" onClick={() => setUploadItems([])}>{t({ id: 'library.upload.clear' })}</button>}
          </div>
          <div className={styles.queueItems}>
            {uploadItems.map((item) => (
              <article className={styles.queueItem} key={item.id}>
                <div className={styles.fileMark}>{item.file.type === 'image/png' ? 'PNG' : 'JPG'}</div>
                <div className={styles.queueDetails}>
                  <div className={styles.queueMeta}>
                    <strong>{item.file.name}</strong>
                    <span>{formatBytes(item.file.size)} · {{
                      waiting: t({ id: 'library.upload.waiting' }),
                      uploading: t({ id: 'library.upload.uploading' }),
                      processing: t({ id: 'library.upload.processing' }),
                      completed: t({ id: 'library.upload.completed' }),
                      failed: t({ id: 'library.upload.failedStatus' }),
                    }[item.status]}</span>
                  </div>
                  <Progress
                    percent={item.progress}
                    size="small"
                    status={item.status === 'failed' ? 'exception' : item.status === 'completed' ? 'success' : 'active'}
                    showInfo={false}
                  />
                  {item.error && <p className={styles.uploadError}>{item.error}</p>}
                </div>
                {item.status === 'failed' && !validateFile(item.file, t) && (
                  <Button size="small" disabled={uploading} onClick={() => void retryUpload(item)}>{t({ id: 'common.action.retry' })}</Button>
                )}
              </article>
            ))}
          </div>
        </section>
      )}

      <section className={styles.collection}>
        <div className={styles.collectionHeader}>
          <h2>{filter === 'favorites' ? t({ id: 'library.collection.favorites' }) : t({ id: 'library.collection.recent' })}</h2>
          <span>{t({ id: 'library.collection.count' }, { count: visiblePhotos.length })}</span>
        </div>

        {loading ? (
          <div className={styles.loadingGrid}>
            {Array.from({ length: 6 }).map((_, index) => <Skeleton.Image key={index} active />)}
          </div>
        ) : loadError ? (
          <Alert
            className={styles.loadError}
            type="error"
            showIcon
            message={t({ id: 'library.load.failed' })}
            description={loadError}
            action={<Button onClick={() => void loadPhotos()}>{t({ id: 'common.action.retry' })}</Button>}
          />
        ) : visiblePhotos.length === 0 ? (
          <div className={styles.empty}>
            {search || filter === 'favorites' ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={search ? t({ id: 'library.empty.search' }) : t({ id: 'library.empty.favorite' })}
              />
            ) : (
              <>
                <div className={styles.emptyMark}>+</div>
                <div className={styles.emptyCopy}>
                  <h3>{t({ id: 'library.empty.title' })}</h3>
                  <p>{t({ id: 'library.empty.description' })}</p>
                </div>
                <Button type="primary" loading={uploading} onClick={openFilePicker}>{t({ id: 'library.empty.uploadFirst' })}</Button>
              </>
            )}
          </div>
        ) : (
          <div className={styles.photoGrid}>
            {visiblePhotos.map((photo) => (
              <article
                key={photo.id}
                className={styles.photoCard}
                role="button"
                tabIndex={0}
                onClick={() => history.push(`/photos/${photo.id}`)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') history.push(`/photos/${photo.id}`);
                }}
              >
                {failedThumbnails.has(photo.id) ? (
                  <div className={styles.photoFallback}><span>{t({ id: 'common.image.unavailable' })}</span><small>{photo.originalFilename}</small></div>
                ) : (
                  <img src={photo.thumbnailUrl} alt={photo.originalFilename} loading="lazy" onError={() => setFailedThumbnails((current) => new Set(current).add(photo.id))} />
                )}
                <button
                  className={`${styles.favorite} ${photo.favorite ? styles.favoriteActive : ''}`}
                  type="button"
                  aria-label={photo.favorite ? t({ id: 'library.favorite.remove' }) : t({ id: 'library.favorite.add' })}
                  onClick={(event) => void handleFavorite(event, photo)}
                >
                  {photo.favorite ? '♥' : '♡'}
                </button>
                <div className={styles.photoMeta}>
                  <div>
                    <strong>{photo.locationName || photo.originalFilename}</strong>
                    <span>{intl.formatDate(new Date(photo.createTime))}</span>
                  </div>
                  <span className={styles.status}>{({
                    PENDING: t({ id: 'library.analysis.pending' }),
                    ANALYZING: t({ id: 'library.analysis.running' }),
                    COMPLETED: t({ id: 'library.analysis.completed' }),
                    FAILED: t({ id: 'library.analysis.unavailable' }),
                  } as const)[photo.analysisStatus]}</span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

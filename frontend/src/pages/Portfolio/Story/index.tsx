import type { CSSProperties, MouseEvent as ReactMouseEvent, PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { history, useIntl, useParams } from 'umi';
import { getPortfolio, getSharedPortfolio } from '@/services/portfolio';
import styles from './index.less';
import { localizedPhotoDescription } from '@/i18n/photo';
import { localizeJapanesePhotos } from '@/services/photo';

const FRAME_DURATION = 3000;

type StoryStyle = CSSProperties & Record<
  '--x' | '--z' | '--yaw' | '--scale' | '--opacity' | '--blur' | '--brightness' |
  '--saturation' | '--card-width' | '--aspect' | '--delay',
  string
>;

const normalizeIndex = (value: number, total: number) => ((value % total) + total) % total;

const relativePosition = (photoIndex: number, activeIndex: number, total: number) => {
  let position = (photoIndex - activeIndex + total) % total;
  if (position > total / 2) position -= total;
  if (total % 2 === 0 && position === total / 2) position = -position;
  return position;
};

export default function PortfolioStoryPage() {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const { portfolioId, shareToken } = useParams<{ portfolioId?: string; shareToken?: string }>();
  const publicMode = Boolean(shareToken);
  const [portfolio, setPortfolio] = useState<API.Portfolio>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [revealed, setRevealed] = useState(false);
  const [chromeVisible, setChromeVisible] = useState(true);
  const [failedImages, setFailedImages] = useState<Set<number>>(new Set());
  const stageRef = useRef<HTMLElement>(null);
  const chromeTimerRef = useRef<number>();
  const activeIndexRef = useRef(0);
  const pointerStartRef = useRef<{ x: number; y: number }>();
  const ignoreClickRef = useRef(false);

  useEffect(() => {
    setLoading(true);
    setLoadError(undefined);
    const loader = shareToken ? getSharedPortfolio(shareToken) : getPortfolio(Number(portfolioId));
    loader
      .then((response) => {
        setPortfolio(response.data);
        const missingIds = response.data.photos
          .filter((photo) => photo.aiDescription && !photo.localizedAiDescription)
          .map((photo) => photo.id);
        if (!publicMode && intl.locale.startsWith('ja') && missingIds.length) {
          void localizeJapanesePhotos(missingIds).then((localized) => {
            const byId = new Map(localized.data.map((photo) => [photo.id, photo]));
            setPortfolio((current) => current ? {
              ...current,
              photos: current.photos.map((photo) => byId.get(photo.id) ?? photo),
            } : current);
          }).catch(() => undefined);
        }
        activeIndexRef.current = 0;
        setIndex(0);
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => setRevealed(true)));
      })
      .catch((error) => setLoadError(error instanceof Error ? error.message : t({ id: 'story.load.failed' })))
      .finally(() => setLoading(false));
  }, [intl.locale, portfolioId, publicMode, shareToken, t]);

  const total = portfolio?.photos.length ?? 0;
  const focusPhoto = useCallback((photoIndex: number) => {
    if (!total) return;
    const nextIndex = normalizeIndex(photoIndex, total);
    activeIndexRef.current = nextIndex;
    setIndex(nextIndex);
  }, [total]);

  const next = useCallback(() => focusPhoto(activeIndexRef.current + 1), [focusPhoto]);
  const previous = useCallback(() => focusPhoto(activeIndexRef.current - 1), [focusPhoto]);

  useEffect(() => {
    if (!playing || total <= 1) return undefined;
    const timer = window.setTimeout(next, FRAME_DURATION);
    return () => window.clearTimeout(timer);
  }, [playing, total, index, next]);

  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') next();
      if (event.key === 'ArrowLeft') previous();
      if (event.key === ' ') { event.preventDefault(); setPlaying((value) => !value); }
      if (event.key === 'Escape') exitStory();
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [next, previous, portfolioId, publicMode]);

  useEffect(() => () => {
    if (chromeTimerRef.current) window.clearTimeout(chromeTimerRef.current);
  }, []);

  const exitStory = () => {
    if (publicMode) history.push('/user/register');
    else history.push(`/portfolios/${portfolioId}`);
  };

  const showChrome = () => {
    setChromeVisible(true);
    if (chromeTimerRef.current) window.clearTimeout(chromeTimerRef.current);
    chromeTimerRef.current = window.setTimeout(() => setChromeVisible(false), 2600);
  };

  const handlePointerMove = (event: ReactMouseEvent<HTMLElement>) => {
    showChrome();
    const x = (event.clientX / window.innerWidth - .5) * 2;
    const y = (event.clientY / window.innerHeight - .5) * 2;
    stageRef.current?.style.setProperty('--bg-x', `${(-x * 4).toFixed(2)}px`);
    stageRef.current?.style.setProperty('--bg-y', `${(-y * 3).toFixed(2)}px`);
    stageRef.current?.style.setProperty('--stage-x', `${(-x * 5).toFixed(2)}px`);
    stageRef.current?.style.setProperty('--stage-y', `${(-y * 3).toFixed(2)}px`);
  };

  const handlePointerDown = (event: ReactPointerEvent<HTMLElement>) => {
    pointerStartRef.current = { x: event.clientX, y: event.clientY };
    ignoreClickRef.current = false;
  };

  const handlePointerUp = (event: ReactPointerEvent<HTMLElement>) => {
    const start = pointerStartRef.current;
    pointerStartRef.current = undefined;
    if (!start || total <= 1) return;
    const deltaX = event.clientX - start.x;
    const deltaY = event.clientY - start.y;
    if (Math.abs(deltaX) > 48 && Math.abs(deltaX) > Math.abs(deltaY) * 1.2) {
      ignoreClickRef.current = true;
      if (deltaX < 0) next(); else previous();
      showChrome();
    }
  };

  const markImageFailed = (photoId: number) => {
    setFailedImages((current) => new Set(current).add(photoId));
  };

  const cardStyle = (photo: API.Photo, photoIndex: number): StoryStyle => {
    const position = relativePosition(photoIndex, index, total);
    const distance = Math.abs(position);
    const aspect = photo.width && photo.height ? Math.max(.56, Math.min(1.9, photo.width / photo.height)) : 1.4;
    const portrait = aspect < .92;
    const visibleDistance = total <= 3 ? 1 : total <= 7 ? 2 : 3;
    const direction = position === 0 ? 0 : position > 0 ? 1 : -1;
    const distanceX = distance === 1 ? 'clamp(285px, 32vw, 540px)'
      : distance === 2 ? 'clamp(430px, 43vw, 760px)'
        : 'clamp(520px, 49vw, 900px)';
    const x = distance === 0 ? '0px' : direction > 0 ? distanceX : `calc(0px - ${distanceX})`;
    return {
      '--x': x,
      '--z': distance === 0 ? '90px' : distance === 1 ? '-130px' : distance === 2 ? '-310px' : '-460px',
      '--yaw': `${direction * (distance === 1 ? -22 : distance === 2 ? -33 : -40)}deg`,
      '--scale': distance === 0 ? '1' : distance === 1 ? '.82' : distance === 2 ? '.68' : '.58',
      '--opacity': distance === 0 ? '1' : distance === 1 ? '.3' : distance === 2 ? '.13' : distance === 3 ? '.055' : '0',
      '--blur': distance === 0 ? '0px' : distance === 1 ? '1.1px' : distance === 2 ? '2.4px' : '4px',
      '--brightness': distance === 0 ? '1' : distance === 1 ? '.58' : distance === 2 ? '.38' : '.25',
      '--saturation': distance === 0 ? '1' : distance === 1 ? '.38' : '.18',
      '--card-width': portrait ? 'min(31vw, 420px)' : aspect > 1.2 ? 'min(48vw, 740px)' : 'min(38vw, 550px)',
      '--aspect': `${aspect}`,
      '--delay': revealed ? '0ms' : `${Math.min(photoIndex, 10) * 45}ms`,
      zIndex: 100 - distance,
      pointerEvents: distance <= visibleDistance ? 'auto' : 'none',
    };
  };

  if (loading) return <div className={styles.loading}><span>{t({ id: 'story.loading' })}</span><i /></div>;
  if (loadError) return <div className={styles.state}><p>{t({ id: 'story.unavailableLabel' })}</p><h1>{t({ id: 'story.unavailable' })}</h1><span>{loadError}</span><button type="button" onClick={exitStory}>{publicMode ? t({ id: 'story.createOwn' }) : t({ id: 'story.returnPortfolios' })}</button></div>;
  if (!portfolio || total === 0) return <div className={styles.state}><p>{t({ id: 'story.emptyLabel' })}</p><h1>{t({ id: 'story.empty' })}</h1><span>{t({ id: 'story.emptyHelp' })}</span><button type="button" onClick={exitStory}>{publicMode ? t({ id: 'story.createOwn' }) : t({ id: 'story.returnPortfolio' })}</button></div>;

  const photo = portfolio.photos[index];

  return (
    <main
      ref={stageRef}
      className={`${styles.story} ${revealed ? styles.revealed : ''} ${chromeVisible ? styles.chromeVisible : styles.chromeHidden} ${total === 1 ? styles.single : ''}`}
      onMouseMove={handlePointerMove}
      onMouseLeave={() => setChromeVisible(false)}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
    >
      <div className={styles.grain} />
      <div className={styles.ambient} style={{ backgroundImage: `url(${photo.thumbnailUrl})` }} />
      <div className={styles.vignette} />
      <div className={styles.horizonGlow} />

      <section className={styles.cardStage} aria-label={t({ id: 'story.stageAria' })}>
        <div className={styles.floorShadow} />
        <div className={styles.cylinder}>
          {portfolio.photos.map((item, photoIndex) => {
            const position = relativePosition(photoIndex, index, total);
            const active = position === 0;
            const failed = failedImages.has(item.id);
            return (
              <button
                key={item.id}
                type="button"
                className={`${styles.card} ${active ? styles.activeCard : ''}`}
                style={cardStyle(item, photoIndex)}
                aria-label={t({ id: active ? 'story.currentPhoto' : 'story.showPhoto' }, { number: photoIndex + 1 })}
                aria-current={active ? 'true' : undefined}
                onClick={() => {
                  if (ignoreClickRef.current) { ignoreClickRef.current = false; return; }
                  focusPhoto(photoIndex);
                  showChrome();
                }}
              >
                <span className={styles.cardSurface}>
                  {failed ? (
                    <span className={styles.imageFallback}><b>{t({ id: 'common.image.unavailable' })}</b><small>{item.originalFilename}</small></span>
                  ) : (
                    <img src={Math.abs(position) <= 1 ? item.originalUrl : item.thumbnailUrl} alt={item.originalFilename} draggable={false} onError={() => markImageFailed(item.id)} />
                  )}
                </span>
              </button>
            );
          })}
        </div>
      </section>

      <header className={styles.header}>
        <button className={styles.exitButton} type="button" aria-label={t({ id: 'story.exit' })} onClick={exitStory}>×</button>
        <div className={styles.titleBlock}><span>{portfolio.title}</span><small>{portfolio.subtitle}</small></div>
        {publicMode ? <button className={styles.createButton} type="button" onClick={() => history.push('/user/register')}>{t({ id: 'story.createOwn' })}</button> : <span />}
      </header>

      {total > 1 && <>
        <button className={`${styles.nav} ${styles.previous}`} type="button" aria-label={t({ id: 'story.previous' })} onClick={previous}>‹</button>
        <button className={`${styles.nav} ${styles.next}`} type="button" aria-label={t({ id: 'story.next' })} onClick={next}>›</button>
      </>}

      <div className={styles.caption} aria-live="polite">
        <span>{photo.locationName || localizedPhotoDescription(photo, intl) || t({ id: 'story.untitled' })}</span>
        <small>{[photo.takenAt && new Date(photo.takenAt).getFullYear(), photo.cameraModel, photo.isoValue && `ISO ${photo.isoValue}`].filter(Boolean).join(' · ') || t({ id: 'story.archivePhoto' })}</small>
      </div>

      <footer className={styles.footer}>
        <button type="button" disabled={total === 1} onClick={() => setPlaying((value) => !value)}><span className={styles.controlIcon}>{playing && total > 1 ? 'Ⅱ' : '▶'}</span>{playing && total > 1 ? t({ id: 'story.pause' }) : t({ id: 'story.play' })}</button>
        <div className={styles.timeline}><div className={styles.track}>{playing && total > 1 && <i key={index} style={{ animationDuration: `${FRAME_DURATION}ms` }} />}</div><span>{String(index + 1).padStart(2, '0')} <b>/</b> {String(total).padStart(2, '0')}</span></div>
      </footer>
    </main>
  );
}

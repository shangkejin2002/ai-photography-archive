import { Button, Empty, Input, message, Spin, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { history, useIntl } from 'umi';
import { createPortfolio, previewAICreate } from '@/services/portfolio';
import { localizedPhotoTagName } from '@/i18n/photo';
import styles from './index.less';
import diagnosticStyles from './diagnostics.less';

export default function AICreatePage() {
  const intl = useIntl();
  const { formatMessage: t } = intl;
  const [prompt, setPrompt] = useState('');
  const [preview, setPreview] = useState<API.AICreatePreview>();
  const [candidates, setCandidates] = useState<API.PhotoCandidate[]>([]);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [coverPhotoId, setCoverPhotoId] = useState<number>();
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const [searching, setSearching] = useState(false);
  const [creating, setCreating] = useState(false);
  const [dragId, setDragId] = useState<number>();
  const examples = ['aiCreate.example.1', 'aiCreate.example.2', 'aiCreate.example.3'].map((id) => t({ id }));

  const selectedCandidates = useMemo(
    () => candidates.filter((candidate) => selectedIds.includes(candidate.photo.id)),
    [candidates, selectedIds],
  );

  const intentTags = useMemo(() => {
    if (!preview) return [];
    const intent = preview.intent;
    return [
      intent.year && `${intent.year}`, intent.season, intent.location, intent.iso && `ISO ${intent.iso}`,
      intent.favoriteOnly && t({ id: 'aiCreate.intent.favorites' }), intent.cameraModel, intent.lensModel,
      intent.diversityRequested && t({ id: 'aiCreate.intent.diverse' }),
      intent.orientation && t({ id: `aiCreate.intent.orientation.${intent.orientation}`, defaultMessage: intent.orientation }),
      intent.shutterSpeed && t({ id: 'aiCreate.intent.shutter' }, { value: intent.shutterSpeed }),
      intent.aperture && `f/${intent.aperture}`,
      intent.focalLength && `${intent.focalLength}mm`,
      intent.hasPeople === true && t({ id: 'aiCreate.intent.peopleRequired' }),
      intent.hasPeople === false && t({ id: 'aiCreate.intent.noPeople' }),
      intent.peopleCount != null && t({ id: 'aiCreate.intent.peopleCount' }, { count: intent.peopleCount }),
      ...intent.subjects.map((tag) => localizedPhotoTagName(tag, intl)),
      ...intent.colors.map((tag) => localizedPhotoTagName(tag, intl)),
      ...intent.visualTags.map((tag) => localizedPhotoTagName(tag, intl)),
      ...(intent.backgroundColors ?? []).map((color) => t({ id: 'aiCreate.intent.background' }, { value: localizedPhotoTagName(color, intl) })),
      ...(intent.moods ?? []).map((mood) => t({ id: 'aiCreate.intent.mood' }, { value: localizedPhotoTagName(mood, intl) })),
      ...(intent.requiredTags ?? []).map((tag) => t({ id: 'aiCreate.intent.required' }, { value: localizedPhotoTagName(tag, intl) })),
      ...(intent.preferredTags ?? []).map((tag) => t({ id: 'aiCreate.intent.preferred' }, { value: localizedPhotoTagName(tag, intl) })),
      ...intent.excludeTags.map((tag) => t({ id: 'aiCreate.intent.exclude' }, { value: localizedPhotoTagName(tag, intl) })),
      t({ id: 'aiCreate.intent.top' }, { count: intent.limit }),
    ].filter(Boolean) as string[];
  }, [intl, preview, t]);

  const handleSearch = async () => {
    const value = prompt.trim();
    if (!value) return void message.info(t({ id: 'aiCreate.prompt.required' }));
    setSearching(true);
    try {
      const result = (await previewAICreate(value)).data;
      setPreview(result);
      setCandidates(result.candidates ?? []);
      const ids = (result.candidates ?? []).map((candidate) => candidate.photo.id);
      setSelectedIds(ids);
      setCoverPhotoId(ids[0]);
      setTitle(result.suggestedTitle);
      setSubtitle(result.suggestedSubtitle);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'aiCreate.prompt.failed' }));
    } finally { setSearching(false); }
  };

  const togglePhoto = (photoId: number) => {
    setSelectedIds((current) => current.includes(photoId) ? current.filter((id) => id !== photoId) : [...current, photoId]);
    if (coverPhotoId === photoId) setCoverPhotoId(selectedIds.find((id) => id !== photoId));
  };

  const dropBefore = (targetId: number) => {
    if (!dragId || dragId === targetId) return;
    setCandidates((current) => {
      const next = [...current];
      const from = next.findIndex((candidate) => candidate.photo.id === dragId);
      const to = next.findIndex((candidate) => candidate.photo.id === targetId);
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
    setDragId(undefined);
  };

  const handleCreate = async () => {
    if (!title.trim() || selectedCandidates.length === 0) return void message.info(t({ id: 'aiCreate.create.required' }));
    setCreating(true);
    try {
      const response = await createPortfolio({
        title: title.trim(), subtitle: subtitle.trim(), sourcePrompt: prompt.trim(),
        coverPhotoId: coverPhotoId ?? selectedCandidates[0].photo.id,
        photoIds: selectedCandidates.map((candidate) => candidate.photo.id),
      });
      message.success(t({ id: 'aiCreate.create.success' }));
      history.push(`/portfolios/${response.data.id}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t({ id: 'aiCreate.create.failed' }));
    } finally { setCreating(false); }
  };

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <p>Stage 6 · AI Create</p>
        <h1>{t({ id: 'aiCreate.hero' }).split('\n').map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1>
        <span>{t({ id: 'aiCreate.description' })}</span>
      </section>
      <section className={styles.promptPanel}>
        <Input.TextArea value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder={t({ id: 'aiCreate.placeholder' })} autoSize={{ minRows: 3, maxRows: 6 }} maxLength={1000} />
        <div className={styles.examples}>{examples.map((example) => <button key={example} type="button" onClick={() => setPrompt(example)}>{example}</button>)}</div>
        <Button type="primary" size="large" loading={searching} onClick={() => void handleSearch()}>{t({ id: 'aiCreate.find' })}</Button>
      </section>
      {searching && <div className={styles.searching}><Spin /><span>{t({ id: 'aiCreate.searching' })}</span></div>}
      {preview && !searching && <>
        <section className={styles.intentBar}>
          <div><span>{t({ id: 'aiCreate.conditions' })}</span><strong>{t({ id: 'aiCreate.candidates' }, { count: candidates.length })}</strong></div>
          <div>{intentTags.map((tag) => <Tag bordered={false} key={tag}>{tag}</Tag>)}</div>
        </section>
        {candidates.length === 0 ? <div className={`${styles.empty} ${diagnosticStyles.emptyEnhancement}`}>
          <Empty description={t({ id: 'aiCreate.empty' })} />
          {!!preview.diagnostics?.length && <div className={diagnosticStyles.diagnostics}>
            <strong>{t({ id: 'aiCreate.diagnostics' })}</strong>
            {preview.diagnostics.map((item) => <p key={item}>{item}</p>)}
          </div>}
          {!!preview.suggestions?.length && <div className={diagnosticStyles.suggestions}>
            <span>{t({ id: 'aiCreate.suggestions' })}</span>
            {preview.suggestions.map((item) => <Tag bordered={false} key={item}>{item}</Tag>)}
          </div>}
        </div> :
          <section className={styles.workspace}>
            <div className={styles.candidateGrid}>{candidates.map((candidate, index) => {
              const selected = selectedIds.includes(candidate.photo.id);
              return <article key={candidate.photo.id} draggable className={`${styles.card} ${selected ? styles.selected : ''}`} onDragStart={() => setDragId(candidate.photo.id)} onDragOver={(event) => event.preventDefault()} onDrop={() => dropBefore(candidate.photo.id)}>
                <img src={candidate.photo.thumbnailUrl} alt={candidate.photo.originalFilename} />
                <button className={styles.selectButton} type="button" onClick={() => togglePhoto(candidate.photo.id)}>{selected ? '✓' : '+'}</button>
                <span className={styles.order}>{String(index + 1).padStart(2, '0')}</span>
                <div className={styles.cardCopy}><strong>{candidate.photo.locationName || candidate.photo.originalFilename}</strong><span>{candidate.matchReasons.map((reason) => localizedPhotoTagName(reason, intl)).join(' · ') || t({ id: 'aiCreate.relevance' }, { score: candidate.score })}</span></div>
                {selected && <button className={`${styles.coverButton} ${coverPhotoId === candidate.photo.id ? styles.coverActive : ''}`} type="button" onClick={() => setCoverPhotoId(candidate.photo.id)}>{coverPhotoId === candidate.photo.id ? t({ id: 'aiCreate.cover' }) : t({ id: 'aiCreate.setCover' })}</button>}
              </article>;
            })}</div>
            <aside className={styles.confirmPanel}>
              <p>{t({ id: 'aiCreate.details' })}</p><h2>{t({ id: 'aiCreate.selected' }, { count: selectedCandidates.length })}</h2>
              <label>{t({ id: 'aiCreate.titleLabel' })}<Input value={title} maxLength={256} onChange={(event) => setTitle(event.target.value)} /></label>
              <label>{t({ id: 'aiCreate.subtitleLabel' })}<Input.TextArea value={subtitle} maxLength={512} autoSize={{ minRows: 2, maxRows: 4 }} onChange={(event) => setSubtitle(event.target.value)} /></label>
              <span>{t({ id: 'aiCreate.reorderHelp' })}</span>
              <Button type="primary" size="large" block loading={creating} onClick={() => void handleCreate()}>{t({ id: 'aiCreate.create' })}</Button>
            </aside>
          </section>}
      </>}
    </div>
  );
}

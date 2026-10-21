import styles from './index.less';

type Props = {
  eyebrow: string;
  title: string;
  description: string;
};

export default function StagePlaceholder({ eyebrow, title, description }: Props) {
  return (
    <section className={styles.page}>
      <p>{eyebrow}</p>
      <h1>{title}</h1>
      <div className={styles.rule} />
      <p className={styles.description}>{description}</p>
    </section>
  );
}

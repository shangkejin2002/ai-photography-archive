import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ts from 'typescript';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

async function importTypeScript(path) {
  const source = await readFile(path, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
}

const zh = (await importTypeScript(join(root, 'src/locales/zh-CN.ts'))).default;
const ja = (await importTypeScript(join(root, 'src/locales/ja-JP.ts'))).default;
assert.deepEqual(Object.keys(zh).sort(), Object.keys(ja).sort(), 'zh-CN and ja-JP resource keys must match');

const locale = await importTypeScript(join(root, 'src/i18n/locale.ts'));
assert.equal(locale.resolveLocale('?lang=ja', 'zh-CN', ['zh-CN']), 'ja-JP');
assert.equal(locale.resolveLocale('?lang=zh', 'ja-JP', ['ja-JP']), 'zh-CN');
assert.equal(locale.resolveLocale('', 'ja-JP', ['zh-CN']), 'ja-JP');
assert.equal(locale.resolveLocale('', null, ['ja-JP']), 'ja-JP');
assert.equal(locale.resolveLocale('', null, ['en-US']), 'zh-CN');
assert.equal(locale.normalizeLocale('ja'), 'ja-JP');
assert.equal(locale.normalizeLocale('zh-Hans-CN'), 'zh-CN');

async function filesUnder(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory() && entry.name.startsWith('.')) return [];
    return entry.isDirectory() ? filesUnder(path) : [path];
  }));
  return nested.flat();
}

const sources = (await filesUnder(join(root, 'src')))
  .filter((path) => ['.ts', '.tsx'].includes(extname(path)))
  .filter((path) => !path.includes('/locales/'));
const missingKeys = new Set();
const chineseLeaks = [];
for (const path of sources) {
  const source = await readFile(path, 'utf8');
  for (const match of source.matchAll(/\bid\s*:\s*['`]([^'`$]+)['`]/g)) {
    if (!(match[1] in zh)) missingKeys.add(match[1]);
  }
  if (!path.endsWith('/components/LanguageSwitcher/index.tsx')) {
    const lines = source.split('\n');
    lines.forEach((line, index) => {
      if (/\p{Script=Han}/u.test(line)) chineseLeaks.push(`${relative(root, path)}:${index + 1}`);
    });
  }
}
assert.deepEqual([...missingKeys], [], `translation keys missing from resources: ${[...missingKeys].join(', ')}`);
assert.deepEqual(chineseLeaks, [], `fixed Chinese text found outside locale resources: ${chineseLeaks.join(', ')}`);

console.log(`i18n checks passed: ${Object.keys(zh).length} paired keys, locale priority, no fixed Chinese leaks`);

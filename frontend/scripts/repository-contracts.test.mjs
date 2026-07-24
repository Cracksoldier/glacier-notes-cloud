import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(frontendRoot, '..');
const generatedRoot = resolve(frontendRoot, 'src/app/shared/generated-api');

test('generated package metadata matches the supported Angular toolchain', () => {
  const packageJson = JSON.parse(readFileSync(resolve(generatedRoot, 'package.json'), 'utf8'));

  assert.equal(packageJson.name, '@glacier-notes/cloud-api');
  assert.equal(packageJson.version, '0.1.0');
  assert.equal(
    packageJson.repository?.url,
    'https://github.com/Cracksoldier/glacier-notes-cloud.git',
  );
  assert.equal(packageJson.peerDependencies?.['@angular/common'], '^22.0.7');
  assert.equal(packageJson.peerDependencies?.['@angular/core'], '^22.0.7');
  assert.equal(packageJson.peerDependencies?.rxjs, '^7.8.0');
  assert.equal(packageJson.devDependencies?.['ng-packagr'], '^22.0.0');
  assert.equal(packageJson.devDependencies?.typescript, '>=6.0 <6.1');
});

test('generation omits the unsafe publishing helper', () => {
  assert.equal(existsSync(resolve(generatedRoot, 'git_push.sh')), false);
});

test('repository-facing frontend metadata uses Glacier Notes commands and names', () => {
  const index = readFileSync(resolve(frontendRoot, 'src/index.html'), 'utf8');
  const readme = readFileSync(resolve(frontendRoot, 'README.md'), 'utf8');
  const contributorGuide = readFileSync(resolve(repositoryRoot, 'AGENTS.md'), 'utf8');

  assert.match(index, /<title>Glacier Notes<\/title>/);
  assert.match(readme, /npm run check/);
  assert.match(readme, /npm run test:ci/);
  assert.match(readme, /npm run test:e2e/);
  assert.doesNotMatch(readme, /\bng e2e\b/);
  assert.match(contributorGuide, /frontend\/src\/app\/shared\/generated-api/);
});

import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(frontendRoot, '..');
const generatedRoot = resolve(frontendRoot, 'src/app/shared/generated-api');

test('generated package metadata matches the supported Angular toolchain', () => {
  const packageJson = JSON.parse(readFileSync(resolve(generatedRoot, 'package.json'), 'utf8'));

  assert.equal(packageJson.name, '@glacier-notes/cloud-api');
  assert.equal(packageJson.version, '0.3.0');
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

test('no generated model carries a Set, which JSON.stringify turns into an empty object', () => {
  const models = readdirSync(resolve(generatedRoot, 'model'));
  const offenders = models.filter((file) =>
    /:\s*Set</.test(readFileSync(resolve(generatedRoot, 'model', file), 'utf8')),
  );

  assert.deepEqual(
    offenders,
    [],
    `uniqueItems: true in the OpenAPI schema produces a Set here, and a request carrying one is ` +
      `rejected by the array schema it came from. Drop uniqueItems in: ${offenders.join(', ')}`,
  );
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

test('the configured backup directory and persistent volume target stay aligned', () => {
  const compose = readFileSync(resolve(repositoryRoot, 'compose.yaml'), 'utf8');

  assert.match(
    compose,
    /GLACIER_BACKUP_DIRECTORY: \$\{GLACIER_BACKUP_DIRECTORY:-\/var\/lib\/glacier-notes\/backups\}/,
  );
  assert.match(
    compose,
    /backup_data:\$\{GLACIER_BACKUP_DIRECTORY:-\/var\/lib\/glacier-notes\/backups\}/,
  );
});

test('the filesystem restore runbook provides an executable image-volume restore', () => {
  const runbook = readFileSync(resolve(repositoryRoot, 'docs/BACKUP_RESTORE.md'), 'utf8');

  assert.match(runbook, /docker compose run --rm --no-deps/);
  assert.match(runbook, /\/restore\/images\/\. \/var\/lib\/glacier-notes\/images\//);
  assert.match(runbook, /chown -R 10001:10001 \/var\/lib\/glacier-notes\/images/);
});

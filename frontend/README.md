# Glacier Notes Frontend

The Angular application consumes the generated client in
`src/app/shared/generated-api`. Change the canonical OpenAPI document first and regenerate from the
repository root; never edit generated files directly.

## Development

Install the locked dependency tree and start the proxied development server:

```bash
npm ci
npm start
```

Open `http://localhost:4200/`. The backend must be available through the development proxy described
in the root setup guide.

## Quality Gates

```bash
npm run check
npm run build:production
npm run test:ci
npm run test:repository
```

Biome is the only formatter and general-purpose frontend linter. Apply safe formatting and lint
fixes with `npm run check:write`.

## Browser Tests

Run Playwright against a configured Glacier Notes deployment:

```bash
GLACIER_E2E_BASE_URL=http://127.0.0.1:8080 \
GLACIER_E2E_USERNAME=member \
GLACIER_E2E_PASSWORD=development-only-password \
npm run test:e2e
```

See the root `README.md` for Docker Compose startup, test-account preparation, and IntelliJ setup.

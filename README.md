# Workout

Web app exported from Grok App Builder. TanStack Start, React 19, Vite, and Tailwind CSS.

## Run locally

```bash
npm install
npm run dev
```

The dev server listens on port 8080.

## Notes

- `node_modules` is not committed. Install dependencies with `npm install`.
- The live-preview OAuth client secret was removed from `src/lib/auth/preview.ts` because this repository is public. Deployed auth uses `GROK_AUTH_CLIENT_SECRET` from the environment instead.

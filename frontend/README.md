# FindBack shared frontend

Member 1 shared React + TypeScript application. Member 2's standalone `member2-ui/` remains separate.

## Development

```sh
npm install
npm run dev
```

Vite serves the app locally and proxies `/api` to `http://localhost:8080`. The frontend uses the Spring-managed session cookie with `credentials: 'include'`; it does not store session identifiers or credentials in browser storage.

Set `VITE_API_BASE_URL` only when the API is hosted at a different base path. The development proxy is intended for local work.

## Build

```sh
npm run build
```

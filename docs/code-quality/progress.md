# Progress

## Frontend (`web-src`)

- Створено клієнт у [`web-src/`](../../web-src/): React + TypeScript + Vite.
- Робочий компонент: каталог організацій (`OrganizationsPage`) через `GET /api/v1/organizations`
- Vite `server.proxy`: `/api` -> `http://localhost:8080`
- Стани UI: loading, error, empty, success; типи з OpenAPI у `src/types/organization.ts`.
- Інтро при відкритті сторінки (друк тексту -> на каталог)
- Інструкції запуску: [`web-src/README.md`](../../web-src/README.md).

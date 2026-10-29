# Frontend | web-src

## Вимоги

- **Node.js** 20+
- npm 10+
- Запущений **backend** на `http://localhost:8080` (див. кореневий [README](../README.md): `docker compose up --build`)

## Запуск

```bash
cd web-src
npm install
npm run dev
```

- http://localhost:5173

Vite проксує `/api` -> `http://localhost:8080`, тож у коді використовуються відносні URL на кшталт `/api/v1/organizations`

**Без бекенда сторінка покаже помилку завантаження списку**

Зібрану версію (nginx, без hot reload) можна підняти з кореня репозиторію: `docker compose up --build`, адреса http://localhost:3000. Образ описано в [`Dockerfile`](Dockerfile), конфіг nginx — у [`docker/nginx.conf`](docker/nginx.conf).

## Скрипти

| Команда           | Опис                                   |
| ----------------- | -------------------------------------- |
| `npm run dev`     | dev-сервер Vite                        |
| `npm run build`   | TypeScript + production-білд у `dist/` |
| `npm run preview` | прев’ю зібраного білду                 |
| `npm run lint`    | лінтер                                 |

## Структура

```
web-src/
├── src/
│   ├── api/              # client.ts + organizations.ts
│   ├── components/       # IntroOverlay, TextType
│   ├── pages/            # Organizations, Events (+ detail placeholders)
│   ├── types/            # Organization, Event, OffsetPage
│   ├── App.tsx           # React Router
│   └── main.tsx
├── vite.config.ts        # proxy /api → :8080
└── package.json
```

Маршрути: `/` → `/organizations`, `/organizations/:organizationId`, `/events`, `/events/:eventId`.

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

## Маршрути

| Шлях | Опис |
|------|------|
| `/organizations`, `/organizations/:id` | Публічний каталог / профіль |
| `/events`, `/events/:id` | Дайджест / деталі + календар |
| `/login` | Вхід (demo admin або mock @ukma.edu.ua) |
| `/register`, `/verify` | Реєстрація + код (US-001 mock на фронті) |
| `/admin/*` | Адмін: org CRUD/logo + кабінет подій |
| `/lab/*` | Моки в пам’яті браузера (потрібен логін) |

Демо-адмін: `bot` / `huyesosik` (або `VITE_DEMO_*`). Реєстрація — лише `@ukma.edu.ua`, код показується на `/verify` (немає email-бекенду). При `401` з API — сесія скидається й редірект на `/login`.

## Структура

```
web-src/
├── src/
│   ├── api/              # HTTP-клієнт + org/events/calendar
│   ├── auth/             # демо-сесія, RequireAuth
│   ├── mock/             # in-memory store для /lab
│   ├── components/
│   ├── pages/            # public + admin/ + lab/
│   ├── types/
│   ├── App.tsx
│   └── main.tsx
├── .env.example
├── vite.config.ts
└── package.json
```

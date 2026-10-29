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
| `/login` | Демо-вхід (UI-gate, не backend Auth) |
| `/admin/*` | Адмін проти live API (потрібен логін) |
| `/lab/*` | Моки в пам’яті браузера (потрібен логін) |

Демо-креденшали за замовчуванням: `bot` / `huyesosik` (або `VITE_DEMO_*` з `.env.example`).

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

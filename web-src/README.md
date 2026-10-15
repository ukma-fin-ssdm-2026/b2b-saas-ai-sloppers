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
│   ├── api/              # HTTP-клієнт
│   ├── components/       # IntroOverlay, TextType
│   ├── pages/            # OrganizationsPage
│   ├── types/            # типи з OpenAPI
│   ├── App.tsx
│   └── main.tsx
├── vite.config.ts        # proxy /api → :8080
└── package.json
```

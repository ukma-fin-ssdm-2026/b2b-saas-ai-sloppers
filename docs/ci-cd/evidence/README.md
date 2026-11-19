# Докази для лабораторної 10

| Файл | Що містить | Як отримано |
|---|---|---|
| [`runs-jobs.json`](runs-jobs.json) | Запуски всіх workflow на `main` (один JSON на рядок) з часом кожного job. Звідси черга й виконання для [`metrics-raw.md`](../metrics-raw.md). | `gh run list --branch main --limit 50 --json databaseId,headSha,workflowName,conclusion,createdAt` + `gh run view <databaseId> --json jobs` |
| [`prs.txt`](prs.txt) | `<merge-коміт> <PR> <authoredDate першого коміту PR>`. Зведення з `pr-commits/`. | з `pr-commits/*.json` |
| [`pr-commits/pr-<n>.json`](pr-commits/) | Повний вивід для 20 PR із таблиці запусків і для чотирьох PR без власного запуску CI (#86, #89, #90, #91). | `gh pr view <n> --json number,title,mergedAt,mergeCommit,commits` |
| [`bonus2/`](bonus2/) | Запуски Static Analysis / Lint / SonarQube Cloud на `main` до і після бонусу 2. | `gh run list --workflow <lint.yml/sonar.yml> --branch main --event push` + `gh run view <databaseId> --json jobs` |

Час у JSON — UTC. У таблицях `docs/ci-cd/*.md` — UTC+2.

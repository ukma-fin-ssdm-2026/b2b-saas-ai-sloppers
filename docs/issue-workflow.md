# Робочий процес завдань (Issue Workflow)

## Життєвий цикл issue
1. **Створення** -- будь-який учасник(зазвичай, керівник трекера завдань) заводить issue із заголовком-дією,
   описом і чеклістом.
2. **Тріаж** -- issue отримує label (тип + пріоритет) та assignee.
3. **В роботі** -- assignee створює гілку і починає роботу.
4. **Review** -- робота оформлюється як Pull Request.
5. **Закриття** -- issue закривається автоматично після merge PR.

## Мітки (labels)
**Тип:** `chore`, `bug`, `documentation`, `duplicate`, `enhacement`, `help wanted`, `invalid`, `question`

**Пріоритет:** `priority: high`, `priority: medium`, `priority: low`

## Іменування гілок
Формат: `<тип>/<номер issue>-<короткий-опис>`

Приклади: `feature/12-user-profile`, `chore/3-branch-protection`

## Зв'язок PR та issue
У описі Pull Request вказуємо `Closes #N`, де N — номер issue.
Після merge у `main` issue закривається автоматично.

## Правила
- Кожна робота ведеться під окремим issue.
- Прямий push у `main` заборонено, тільки через PR.
- Один issue = одна гілка = один PR.

Оформлено за допомогою ШІ
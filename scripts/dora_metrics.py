#!/usr/bin/env python3
"""Чотири метрики DORA з історії GitHub Actions (лабораторна 10, бонус 1).

Визначення ті самі, що в ручному підрахунку (docs/ci-cd/metrics-raw.md):

* розгортання — зелений запуск workflow CI на main;
* черга — від created_at запуску до started_at першого job;
* виконання — від started_at першого job до completed_at останнього;
* час виконання змін — від authoredDate найранішого коміту PR до завершення першого зеленого
  запуску на main, який уже містить merge-коміт цього PR;
* частота збоїв змін — червоні запуски / усі завершені (скасовані теж у знаменнику);
* час відновлення — від завершення червоного запуску до завершення наступного зеленого.

Дані бере через `gh api` (потрібен `gh auth login`), лише читає. Запуск:

    python3 scripts/dora_metrics.py --output docs/ci-cd/dora-auto.md

Без параметрів рахує вікно Етапу 1 (22.10–12.11); інше вікно — `--since` / `--until` в ISO 8601.
"""

from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Iterable, Optional

DEFAULT_REPO = "ukma-fin-ssdm-2026/b2b-saas-ai-sloppers"
DEFAULT_WORKFLOW = "ci.yml"
DEFAULT_BRANCH = "main"
# вікно Етапу 1: від створення першого запуску до завершення останнього (metrics-raw.md)
DEFAULT_SINCE = "2026-10-22T20:27:00Z"
DEFAULT_UNTIL = "2026-11-12T14:37:00Z"
# зелений запуск, що доставляє PR, змердженний під кінець вікна, може початися вже після нього
DELIVERY_LOOKAHEAD = timedelta(days=7)
DISPLAY_TZ = timezone(timedelta(hours=2))  # як у таблицях docs/ci-cd/*.md

FAILED = {"failure", "timed_out", "startup_failure"}


# ---- дані ----


@dataclass(frozen=True)
class Run:
    run_id: int
    head_sha: str
    conclusion: Optional[str]
    created_at: datetime
    first_job_started: Optional[datetime]
    last_job_completed: Optional[datetime]
    attempt: int

    @property
    def queue_min(self) -> Optional[float]:
        if self.first_job_started is None:
            return None
        return minutes(self.first_job_started - self.created_at)

    @property
    def execution_min(self) -> Optional[float]:
        if self.first_job_started is None or self.last_job_completed is None:
            return None
        return minutes(self.last_job_completed - self.first_job_started)

    @property
    def completed_at(self) -> Optional[datetime]:
        return self.last_job_completed

    @property
    def green(self) -> bool:
        return self.conclusion == "success"

    @property
    def red(self) -> bool:
        return self.conclusion in FAILED


@dataclass(frozen=True)
class PullRequest:
    number: int
    merge_sha: str
    merged_at: datetime
    first_commit_at: datetime


@dataclass(frozen=True)
class LeadTime:
    pr: PullRequest
    run: Run
    hours: float
    queue_min: float
    execution_min: float


@dataclass(frozen=True)
class Report:
    since: datetime
    until: datetime
    runs: list  # list[Run], у вікні, найстаріші спершу
    lead_times: list  # list[LeadTime]
    undelivered: list  # list[PullRequest]: зеленого запуску з їхніми змінами ще немає
    restores: list  # list[tuple[Run, Optional[Run]]]: червоний і наступний зелений

    @property
    def weeks(self) -> float:
        return (self.until - self.since) / timedelta(weeks=1)

    @property
    def completed(self) -> list:
        return [r for r in self.runs if r.conclusion is not None]

    @property
    def green_runs(self) -> list:
        return [r for r in self.runs if r.green]

    @property
    def red_runs(self) -> list:
        return [r for r in self.runs if r.red]

    @property
    def reruns(self) -> list:
        return [r for r in self.runs if r.attempt > 1]


# ---- обчислення (без мережі; див. scripts/test_dora_metrics.py) ----


def minutes(delta: timedelta) -> float:
    return delta.total_seconds() / 60


def compute(runs: Iterable[Run], prs: Iterable[PullRequest], main_order: list,
            since: datetime, until: datetime) -> Report:
    """Метрики за вікно [since, until].

    :param main_order: SHA комітів main від найновішого до найстарішого (історія main лінійна:
        PR зливаються squash-ом), тож «запуск містить коміт» означає, що його head не старіший.
    """
    position = {sha: index for index, sha in enumerate(main_order)}
    all_runs = sorted(runs, key=lambda r: r.created_at)
    window = [r for r in all_runs if since <= r.created_at <= until]

    lead_times, undelivered = [], []
    for pr in sorted(prs, key=lambda p: p.merged_at):
        if not since <= pr.merged_at <= until or pr.merge_sha not in position:
            continue
        delivering = [r for r in all_runs
                      if r.green and r.completed_at is not None and r.head_sha in position
                      and position[r.head_sha] <= position[pr.merge_sha]]
        if not delivering:
            undelivered.append(pr)
            continue
        run = min(delivering, key=lambda r: r.completed_at)
        lead_times.append(LeadTime(pr, run, (run.completed_at - pr.first_commit_at) / timedelta(hours=1),
                                   run.queue_min or 0.0, run.execution_min or 0.0))

    restores = []
    for red in (r for r in window if r.red):
        later_green = [r for r in all_runs if r.green and r.completed_at is not None
                       and red.completed_at is not None and r.completed_at > red.completed_at]
        restores.append((red, min(later_green, key=lambda r: r.completed_at) if later_green else None))

    return Report(since, until, window, lead_times, undelivered, restores)


# ---- GitHub API ----


def gh_lines(path: str, jq: str, params: dict) -> list:
    """`gh api --paginate` з одним JSON-об'єктом на рядок."""
    command = ["gh", "api", "-X", "GET", path, "--paginate", "--jq", jq]
    for key, value in params.items():
        command += ["-f", f"{key}={value}"]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    return [json.loads(line) for line in output.splitlines() if line.strip()]


def parse_time(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def fetch_runs(repo: str, workflow: str, branch: str, since: datetime, until: datetime) -> list:
    created = f"{iso(since)}..{iso(until + DELIVERY_LOOKAHEAD)}"
    raw = gh_lines(f"repos/{repo}/actions/workflows/{workflow}/runs", ".workflow_runs[]",
                   {"branch": branch, "event": "push", "per_page": 100, "created": created})
    runs = []
    for run in raw:
        jobs = gh_lines(f"repos/{repo}/actions/runs/{run['id']}/jobs", ".jobs[]", {"per_page": 100})
        started = [parse_time(j["started_at"]) for j in jobs if j.get("started_at")]
        completed = [parse_time(j["completed_at"]) for j in jobs if j.get("completed_at")]
        runs.append(Run(run["id"], run["head_sha"], run["conclusion"], parse_time(run["created_at"]),
                        min(started) if started else None, max(completed) if completed else None,
                        run.get("run_attempt", 1)))
    return runs


def fetch_main_order(repo: str, branch: str) -> list:
    return [c["sha"] for c in gh_lines(f"repos/{repo}/commits", ".[] | {sha}", {"sha": branch, "per_page": 100})]


def fetch_prs(repo: str, branch: str, since: datetime, until: datetime) -> list:
    raw = gh_lines(f"repos/{repo}/pulls", ".[] | {number, merged_at, merge_commit_sha}",
                   {"state": "closed", "base": branch, "per_page": 100})
    prs = []
    for pr in raw:
        merged_at = parse_time(pr["merged_at"])
        if merged_at is None or not since <= merged_at <= until:
            continue
        commits = gh_lines(f"repos/{repo}/pulls/{pr['number']}/commits", ".[] | {date: .commit.author.date}",
                           {"per_page": 100})
        first = min(parse_time(c["date"]) for c in commits)
        prs.append(PullRequest(pr["number"], pr["merge_commit_sha"], merged_at, first))
    return prs


# ---- Markdown ----


def iso(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def local(moment: Optional[datetime], with_date: bool = True) -> str:
    if moment is None:
        return "—"
    return moment.astimezone(DISPLAY_TZ).strftime("%d.%m %H:%M" if with_date else "%H:%M")


def num(value: Optional[float], digits: int = 1) -> str:
    """Десяткова кома, як у ручних таблицях."""
    if value is None:
        return "—"
    return f"{value:.{digits}f}".replace(".", ",")


STATUS = {"success": "зелений", "cancelled": "скасовано", "failure": "червоний", "timed_out": "червоний",
          "startup_failure": "червоний", None: "у процесі"}


def render(report: Report, prs_by_sha: dict, command: str) -> str:
    lines = [
        "# Метрики DORA: автоматичний підрахунок",
        "",
        f"Згенеровано скриптом [`scripts/dora_metrics.py`](../../scripts/dora_metrics.py) "
        f"({datetime.now(DISPLAY_TZ).strftime('%d.%m.%Y %H:%M')}, UTC+2). Не редагувати вручну — перезапустити:",
        "",
        "```bash",
        command,
        "```",
        "",
        f"Вікно: **{local(report.since)} — {local(report.until)}** (UTC+2), "
        f"{num(report.weeks, 2)} тижня. Визначення — як у [`metrics-raw.md`](metrics-raw.md); "
        "розбіжності з ручним підрахунком пояснено в [`dora-summary.md`](dora-summary.md#бонуси).",
        "",
        "## Чотири метрики",
        "",
        "| Метрика | Формула | Результат |",
        "|---|---|---|",
    ]
    green, completed, red = report.green_runs, report.completed, report.red_runs
    lead_hours = [lt.hours for lt in report.lead_times]
    lines.append(f"| Частота розгортань | зелені запуски / тижні | {len(green)} / {num(report.weeks, 2)} = "
                 f"**{num(len(green) / report.weeks)} на тиждень** |")
    if lead_hours:
        lines.append(f"| Час виконання змін | перший коміт PR → зелений запуск на main з його змінами | "
                     f"медіана **{num(statistics.median(lead_hours))} год**, середнє "
                     f"**{num(statistics.mean(lead_hours))} год** (n = {len(lead_hours)} PR) |")
    else:
        lines.append("| Час виконання змін | перший коміт PR → зелений запуск на main з його змінами | немає PR |")
    rate = 100 * len(red) / len(completed) if completed else 0.0
    lines.append(f"| Частота збоїв змін | червоні / завершені × 100% | {len(red)} / {len(completed)} = "
                 f"**{num(rate)}%** |")
    restore_hours = [(green_run.completed_at - red_run.completed_at) / timedelta(hours=1)
                     for red_run, green_run in report.restores if green_run is not None]
    lines.append("| Час відновлення | червоний запуск → наступний зелений | " + (
        f"медіана **{num(statistics.median(restore_hours))} год**, середнє "
        f"**{num(statistics.mean(restore_hours))} год** (n = {len(restore_hours)})" if restore_hours
        else "**немає даних**: червоних запусків у вікні немає") + " |")

    executions = [r.execution_min for r in green if r.execution_min is not None]
    with_queue = [r.execution_min + r.queue_min for r in green if r.execution_min is not None]
    lines += [
        "",
        "Орієнтири CircleCI: частка зелених запусків "
        f"**{num(100 * len(green) / len(completed) if completed else 0)}%** ({len(green)} з {len(completed)}); "
        f"медіана виконання зеленого запуску **{num(statistics.median(executions)) if executions else '—'} хв**, "
        f"із чергою **{num(statistics.median(with_queue)) if with_queue else '—'} хв**.",
    ]

    if report.lead_times:
        total = sum(lt.hours for lt in report.lead_times) * 60
        queue = sum(lt.queue_min for lt in report.lead_times)
        execution = sum(lt.execution_min for lt in report.lead_times)
        lines += [
            "",
            "## Черга і виконання в часі виконання змін",
            "",
            "Сума по PR (запуск, що доставив кілька PR, рахується для кожного, як у ручному підрахунку).",
            "",
            "| Складова | Хв | Частка |",
            "|---|---:|---:|",
            f"| Черга на раннер | {num(queue, 0)} | **{num(100 * queue / total)}%** |",
            f"| Виконання CI | {num(execution, 0)} | **{num(100 * execution / total)}%** |",
            f"| Решта: розробка, рев'ю й очікування злиття | {num(total - queue - execution, 0)} | "
            f"**{num(100 * (total - queue - execution) / total)}%** |",
        ]

    lines += [
        "",
        "## Запуски",
        "",
        "| № | Коміт | PR | Створено | Завершено | Черга, хв | Виконання, хв | Статус | Спроба |",
        "|---|---|---|---|---|---:|---:|---|---:|",
    ]
    for index, run in enumerate(report.runs, start=1):
        pr = prs_by_sha.get(run.head_sha)
        lines.append(f"| {index} | `{run.head_sha[:7]}` | {f'#{pr}' if pr else '—'} | {local(run.created_at)} | "
                     f"{local(run.completed_at)} | {num(run.queue_min)} | {num(run.execution_min)} | "
                     f"{STATUS.get(run.conclusion, run.conclusion)} | {run.attempt} |")

    lines += [
        "",
        "## Час виконання змін по PR",
        "",
        "| PR | Перший коміт | Злито | Зелений запуск з його змінами | Год |",
        "|---|---|---|---|---:|",
    ]
    for lt in report.lead_times:
        own = "" if lt.run.head_sha == lt.pr.merge_sha else " (не власний)"
        lines.append(f"| #{lt.pr.number} | {local(lt.pr.first_commit_at)} | {local(lt.pr.merged_at)} | "
                     f"`{lt.run.head_sha[:7]}` {local(lt.run.completed_at)}{own} | {num(lt.hours)} |")
    for pr in report.undelivered:
        lines.append(f"| #{pr.number} | {local(pr.first_commit_at)} | {local(pr.merged_at)} | ще немає | — |")

    lines += [
        "",
        "## Повторні запуски на main (`run_attempt` > 1)",
        "",
        "Ознака нестабільного тесту: той самий коміт став зеленим після повтору без зміни коду.",
        "",
    ]
    if report.reruns:
        lines += ["| Коміт | Спроба | Статус |", "|---|---:|---|"]
        lines += [f"| `{r.head_sha[:7]}` | {r.attempt} | {STATUS.get(r.conclusion, r.conclusion)} |"
                  for r in report.reruns]
    else:
        lines.append("У вікні немає: кожен запуск має одну спробу.")
    return "\n".join(lines) + "\n"


def main(argv: Optional[list] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--workflow", default=DEFAULT_WORKFLOW)
    parser.add_argument("--branch", default=DEFAULT_BRANCH)
    parser.add_argument("--since", default=DEFAULT_SINCE, help="початок вікна, ISO 8601")
    parser.add_argument("--until", default=DEFAULT_UNTIL, help="кінець вікна, ISO 8601")
    parser.add_argument("--output", help="файл Markdown; без нього — у stdout")
    args = parser.parse_args(argv)

    since, until = parse_time(args.since), parse_time(args.until)
    runs = fetch_runs(args.repo, args.workflow, args.branch, since, until)
    prs = fetch_prs(args.repo, args.branch, since, until)
    report = compute(runs, prs, fetch_main_order(args.repo, args.branch), since, until)

    command = "python3 scripts/dora_metrics.py" + "".join(
        f" --{name} {value}" for name, value, default in [
            ("since", args.since, DEFAULT_SINCE), ("until", args.until, DEFAULT_UNTIL),
            ("output", args.output, None)] if value and value != default)
    markdown = render(report, {pr.merge_sha: pr.number for pr in prs}, command)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as file:
            file.write(markdown)
    else:
        sys.stdout.write(markdown)
    return 0


if __name__ == "__main__":
    sys.exit(main())

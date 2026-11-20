#!/usr/bin/env python3
"""Пошук нестабільних тестів за звітами JUnit з кількох прогонів (лабораторна 10, бонус 4).

Очікує теку з підтеками прогонів, у кожній — звіти JUnit XML (Surefire бекенду, Vitest фронтенду):

    flaky/run-1/**/*.xml
    flaky/run-2/**/*.xml
    ...

Нестабільний тест — той, що в одних прогонах пройшов, а в інших упав. Тест, що падав у кожному
прогоні, — не нестабільний, а зламаний; його виведено окремо. Запуск:

    python3 scripts/flaky_tests.py flaky/ [--fail-on-flaky]

Результат — Markdown у stdout (workflow дописує його в $GITHUB_STEP_SUMMARY).
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

PASSED, FAILED, SKIPPED, MISSING = "passed", "failed", "skipped", "missing"
ICON = {PASSED: "✅", FAILED: "❌", SKIPPED: "⏭", MISSING: "—"}


def outcomes_of(run_dir: Path) -> dict:
    """Результат кожного тесту одного прогону: «клас#тест» → passed / failed / skipped."""
    outcomes = {}
    for report in sorted(run_dir.rglob("*.xml")):
        try:
            root = ElementTree.parse(report).getroot()
        except ElementTree.ParseError:
            continue  # недописаний звіт (прогін упав посередині) — ці тести стануть «missing»
        for case in root.iter("testcase"):
            key = f"{case.get('classname', '')}#{case.get('name', '')}"
            if case.find("failure") is not None or case.find("error") is not None:
                outcome = FAILED
            elif case.find("skipped") is not None:
                outcome = SKIPPED
            else:
                outcome = PASSED
            # той самий тест двічі в одному прогоні (повтор усередині Surefire) — падіння важливіше
            if outcomes.get(key) != FAILED:
                outcomes[key] = outcome
    return outcomes


def analyse(runs: list) -> tuple:
    """:param runs: результати прогонів (див. outcomes_of). :return: (усі тести, нестабільні, зламані)."""
    tests = sorted(set().union(*runs)) if runs else []
    flaky, broken = [], []
    for test in tests:
        seen = [run.get(test, MISSING) for run in runs]
        executed = {outcome for outcome in seen if outcome in (PASSED, FAILED)}
        if executed == {PASSED, FAILED}:
            flaky.append((test, seen))
        elif executed == {FAILED} and all(outcome == FAILED for outcome in seen):
            broken.append((test, seen))
    return tests, flaky, broken


def render(run_names: list, tests: list, flaky: list, broken: list) -> str:
    lines = [
        "## Пошук нестабільних тестів",
        "",
        f"Прогонів: **{len(run_names)}**, тестів: **{len(tests)}**, нестабільних: **{len(flaky)}**, "
        f"падають у кожному прогоні: **{len(broken)}**.",
        "",
    ]
    header = "| Тест | " + " | ".join(run_names) + " |"
    divider = "|---|" + "---|" * len(run_names)
    if flaky:
        lines += ["### Результат відрізнявся між прогонами", "", header, divider]
        lines += [f"| `{test}` | " + " | ".join(ICON[o] for o in seen) + " |" for test, seen in flaky]
        lines.append("")
    else:
        lines += ["Жоден тест не змінював результату між прогонами.", ""]
    if broken:
        lines += ["### Падають у кожному прогоні (зламані, а не нестабільні)", "", header, divider]
        lines += [f"| `{test}` | " + " | ".join(ICON[o] for o in seen) + " |" for test, seen in broken]
        lines.append("")
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("root", type=Path, help="тека з підтеками прогонів run-1, run-2, …")
    parser.add_argument("--fail-on-flaky", action="store_true", help="код виходу 1, якщо знайдено нестабільні")
    args = parser.parse_args(argv)

    run_dirs = sorted((d for d in args.root.iterdir() if d.is_dir()),
                      key=lambda d: (len(d.name), d.name))  # run-2 раніше за run-10
    runs = [outcomes_of(d) for d in run_dirs]
    tests, flaky, broken = analyse(runs)
    sys.stdout.write(render([d.name for d in run_dirs], tests, flaky, broken))
    return 1 if args.fail_on_flaky and flaky else 0


if __name__ == "__main__":
    sys.exit(main())

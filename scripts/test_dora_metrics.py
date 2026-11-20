"""Тести розрахунків dora_metrics.py без мережі: python3 -m unittest scripts/test_dora_metrics.py"""

import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from dora_metrics import PullRequest, Run, compute, num, render  # noqa: E402

T0 = datetime(2026, 11, 1, 12, 0, tzinfo=timezone.utc)


def at(hours: float) -> datetime:
    return T0 + timedelta(hours=hours)


def run(sha: str, conclusion, created: float, queue_min: float = 0, execution_min: float = 2,
        attempt: int = 1) -> Run:
    started = at(created) + timedelta(minutes=queue_min)
    return Run(hash(sha), sha, conclusion, at(created), started, started + timedelta(minutes=execution_min),
               attempt)


class ComputeTest(unittest.TestCase):

    # main від найновішого: d ← c ← b ← a
    MAIN = ["d", "c", "b", "a"]

    def test_own_green_run_delivers_pr(self):
        report = compute([run("a", "success", 10, queue_min=6, execution_min=4)],
                         [PullRequest(1, "a", at(10), at(0))], self.MAIN, at(0), at(48))

        lead = report.lead_times[0]
        self.assertAlmostEqual(lead.hours, 10 + 10 / 60)
        self.assertEqual((lead.queue_min, lead.execution_min), (6, 4))

    def test_cancelled_run_is_delivered_by_next_green_descendant(self):
        # як #77 у ручній таблиці: власний запуск скасовано, зміни доставив запуск #81
        runs = [run("b", "cancelled", 10), run("c", "success", 11)]
        prs = [PullRequest(77, "b", at(10), at(0)), PullRequest(81, "c", at(11), at(5))]

        report = compute(runs, prs, self.MAIN, at(0), at(48))

        self.assertEqual([lt.run.head_sha for lt in report.lead_times], ["c", "c"])
        self.assertEqual(len(report.green_runs), 1)
        self.assertEqual(len(report.completed), 2, "скасований — у знаменнику частоти збоїв")

    def test_pr_without_own_run_counts(self):
        # як #86, #89: власного запуску немає, зміни доставив наступний зелений
        report = compute([run("d", "success", 30)], [PullRequest(89, "b", at(10), at(9))], self.MAIN,
                         at(0), at(48))

        self.assertEqual(report.lead_times[0].run.head_sha, "d")

    def test_older_green_run_does_not_deliver_newer_pr(self):
        report = compute([run("a", "success", 20)], [PullRequest(5, "c", at(10), at(0))], self.MAIN,
                         at(0), at(48))

        self.assertEqual(report.lead_times, [])
        self.assertEqual([pr.number for pr in report.undelivered], [5])

    def test_restore_time_is_red_to_next_green(self):
        runs = [run("a", "failure", 1), run("b", "success", 3), run("c", "success", 5)]

        report = compute(runs, [], self.MAIN, at(0), at(48))

        red, green = report.restores[0]
        self.assertEqual((red.head_sha, green.head_sha), ("a", "b"))
        self.assertEqual(len(report.red_runs), 1)

    def test_window_and_reruns(self):
        runs = [run("a", "success", -5), run("b", "success", 1, attempt=2), run("c", "success", 50)]

        report = compute(runs, [], self.MAIN, at(0), at(48))

        self.assertEqual([r.head_sha for r in report.runs], ["b"])
        self.assertEqual([r.head_sha for r in report.reruns], ["b"])

    def test_render_mentions_every_metric(self):
        report = compute([run("a", "success", 10)], [PullRequest(1, "a", at(10), at(0))], self.MAIN,
                         at(0), at(7 * 24))

        markdown = render(report, {"a": 1}, "python3 scripts/dora_metrics.py")

        for heading in ("Частота розгортань", "Час виконання змін", "Частота збоїв змін", "Час відновлення",
                        "1 / 1,00 = **1,0 на тиждень**", "немає даних"):
            self.assertIn(heading, markdown)

    def test_decimal_comma(self):
        self.assertEqual(num(5.666), "5,7")
        self.assertEqual(num(None), "—")


if __name__ == "__main__":
    unittest.main()

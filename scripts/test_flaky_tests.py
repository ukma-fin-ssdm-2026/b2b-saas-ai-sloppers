"""Тести flaky_tests.py: python3 -m unittest scripts/test_flaky_tests.py"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from flaky_tests import FAILED, PASSED, analyse, main, outcomes_of  # noqa: E402

SUREFIRE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="ua.edu.ukma.ClockTest" tests="3">
  <testcase name="stable" classname="ua.edu.ukma.ClockTest" time="0.01"/>
  <testcase name="timing" classname="ua.edu.ukma.ClockTest" time="0.02">{timing}</testcase>
  <testcase name="ignored" classname="ua.edu.ukma.ClockTest"><skipped/></testcase>
</testsuite>
"""

VITEST = """<?xml version="1.0" encoding="UTF-8" ?>
<testsuites name="vitest tests" tests="1">
  <testsuite name="src/api/ratings.test.ts" tests="1">
    <testcase classname="src/api/ratings.test.ts" name="API рейтингів &gt; оцінка організації" time="0.003">{body}</testcase>
  </testsuite>
</testsuites>
"""

FAILURE = '<failure message="expected 1 to be 2">AssertionError</failure>'


class FlakyTestsTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write_run(self, name, timing="", vitest=""):
        run = self.root / name
        (run / "backend").mkdir(parents=True)
        (run / "frontend").mkdir(parents=True)
        (run / "backend" / "TEST-ua.edu.ukma.ClockTest.xml").write_text(SUREFIRE.format(timing=timing))
        (run / "frontend" / "junit.xml").write_text(VITEST.format(body=vitest))
        return run

    def test_parses_surefire_and_vitest(self):
        outcomes = outcomes_of(self.write_run("run-1", timing=FAILURE))

        self.assertEqual(outcomes["ua.edu.ukma.ClockTest#stable"], PASSED)
        self.assertEqual(outcomes["ua.edu.ukma.ClockTest#timing"], FAILED)
        self.assertEqual(outcomes["ua.edu.ukma.ClockTest#ignored"], "skipped")
        self.assertEqual(outcomes["src/api/ratings.test.ts#API рейтингів > оцінка організації"], PASSED)

    def test_error_counts_as_failure(self):
        outcomes = outcomes_of(self.write_run("run-1", timing='<error type="NullPointerException"/>'))

        self.assertEqual(outcomes["ua.edu.ukma.ClockTest#timing"], FAILED)

    def test_flaky_only_when_result_changes(self):
        runs = [outcomes_of(self.write_run(f"run-{i}", timing=FAILURE if i == 3 else "",
                                           vitest=FAILURE))
                for i in range(1, 6)]

        tests, flaky, broken = analyse(runs)

        self.assertEqual([t for t, _ in flaky], ["ua.edu.ukma.ClockTest#timing"])
        self.assertEqual(flaky[0][1], [PASSED, PASSED, FAILED, PASSED, PASSED])
        self.assertEqual([t for t, _ in broken], ["src/api/ratings.test.ts#API рейтингів > оцінка організації"])
        self.assertEqual(len(tests), 4)

    def test_missing_in_crashed_run_is_not_flaky(self):
        self.write_run("run-1")
        run_2 = self.root / "run-2"
        run_2.mkdir()
        (run_2 / "TEST-broken.xml").write_text("<testsuite><testcase name=")  # обірваний звіт

        crashed = outcomes_of(run_2)
        tests, flaky, broken = analyse([outcomes_of(self.root / "run-1"), crashed])

        self.assertEqual(crashed, {})
        self.assertEqual((flaky, broken), ([], []), "відсутній у прогоні тест не рахується ні нестабільним, ні зламаним")

    def test_exit_code_and_run_order(self):
        for i in (1, 2, 10):
            self.write_run(f"run-{i}", timing=FAILURE if i == 10 else "")

        self.assertEqual(main([str(self.root)]), 0, "без --fail-on-flaky лише звіт")
        self.assertEqual(main([str(self.root), "--fail-on-flaky"]), 1)


if __name__ == "__main__":
    unittest.main()

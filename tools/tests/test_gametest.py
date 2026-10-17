# The Orbitalis Developers
#
# Copyright 2026 Vsevolod Petrov (lazyhat)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Disposable world ownership, retention and native region accounting."""
import json
import os
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gametest


class GameTestLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'runs'

    def world(self, run_id):
        run = gametest.prepare(self.root, run_id)
        region = run / 'world/dimensions/orbitalis/space/region'
        region.mkdir(parents=True)
        (region / 'r.0.0.mca').write_bytes(struct.pack('>1024I', 513, 769, *([0] * 1022)) + bytes(12288))
        return run

    def test_success_removes_only_owned_world_and_keeps_metrics_and_report(self):
        first = self.world('first')
        failed = self.world('failed')
        for name in ('mods', '.sable', 'logs'):
            (first / name).mkdir()
            (first / name / 'payload').write_bytes(b'fixture')
        report = Path(self.temp.name) / 'results.xml'
        report.write_text('<testsuite/>')
        metrics = gametest.finish(self.root, 'first', True, report)
        self.assertFalse((first / 'world').exists())
        self.assertFalse((first / 'mods').exists())
        self.assertFalse((first / '.sable').exists())
        self.assertEqual((first / 'logs/payload').read_bytes(), b'fixture')
        self.assertTrue((failed / 'world').exists())
        self.assertEqual((first / 'results.xml').read_text(), report.read_text())
        self.assertEqual(json.loads((first / 'metrics.json').read_text()), metrics)
        self.assertEqual(metrics['regions']['dimensions/orbitalis/space/region'],
                         {'files': 1, 'bytes': 16384, 'chunks': 2, 'short_headers': 0})

    def test_failure_preserves_world_and_marks_incomplete_regions(self):
        run = self.world('failed')
        (run / 'world/region').mkdir()
        (run / 'world/region/r.1.1.mca').touch()
        metrics = gametest.finish(self.root, 'failed')
        self.assertFalse(metrics['success'])
        self.assertTrue((run / 'world').exists())
        self.assertEqual(metrics['regions']['region']['short_headers'], 1)

    def test_never_adopts_existing_run_or_legacy_world(self):
        run = self.world('old')
        with self.assertRaises(FileExistsError):
            gametest.prepare(self.root, 'old')
        self.assertTrue((run / 'world').exists())
        legacy = self.root / 'legacy'
        legacy.mkdir()
        (legacy / 'keep').touch()
        self.assertIsNone(gametest.finish(self.root, 'legacy', True))
        self.assertTrue((legacy / 'keep').exists())

    def test_rejects_parent_escape_and_mismatched_ownership(self):
        for run_id in ('..', '../world', '/world', ''):
            with self.assertRaises(ValueError):
                gametest.prepare(self.root, run_id)
        run = self.world('owned')
        (run / gametest.MARKER).write_text(json.dumps({'run': 'another'}))
        with self.assertRaises(ValueError):
            gametest.finish(self.root, 'owned', True)
        self.assertTrue((run / 'world').exists())

    def test_symlinks_are_not_followed_or_deleted(self):
        run = self.world('linked')
        target = Path(self.temp.name) / 'user-save'
        target.mkdir()
        (target / 'keep').touch()
        (run / 'world/link').symlink_to(target, target_is_directory=True)
        with self.assertRaises(ValueError):
            gametest.finish(self.root, 'linked', True)
        self.assertTrue((target / 'keep').exists())
        with self.assertRaises(ValueError):
            gametest.prepare(run / 'world/link', 'new')

    def test_finalizer_before_prepare_is_noop_and_finished_run_cannot_be_reused(self):
        self.assertIsNone(gametest.finish(self.root, 'missing', True))
        self.world('once')
        gametest.finish(self.root, 'once', True)
        with self.assertRaises(ValueError):
            gametest.finish(self.root, 'once', True)

    def test_empty_loom_directory_is_owned_but_stale_report_is_not_retained(self):
        run = self.root / 'empty'
        run.mkdir(parents=True)
        report = Path(self.temp.name) / 'old.xml'
        report.write_text('<testsuite/>')
        os.utime(report, (1, 1))
        gametest.prepare(self.root, 'empty')
        gametest.finish(self.root, 'empty', False, report)
        self.assertTrue((run / 'metrics.json').is_file())
        self.assertFalse((run / 'results.xml').exists())

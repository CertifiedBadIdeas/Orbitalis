# The Compukters Developers
#
# Copyright 2026 The Compukters Developers
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

# Adapted for Orbitalis: independent tooling namespace and repository prerequisites.
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

RUNNER = Path(__file__).with_name('run.py').resolve()


class BuildGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'workspace'
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Test', '-c',
                        'user.email=test@example.com', 'commit', '-qm', 'init', '--allow-empty'], check=True)
        self.linked = Path(self.temp.name) / 'linked'
        subprocess.run(['git', '-C', str(self.root), 'worktree', 'add', '-qb', 'task', str(self.linked)],
                       check=True, stdout=subprocess.DEVNULL)
        self.probe = Path(self.temp.name) / 'probe'
        self.probe.write_text('''#!/usr/bin/env python3
import json,sys,time
from pathlib import Path
log=Path(sys.argv[-2]); label=sys.argv[-1]
with log.open('a') as f: f.write(json.dumps([label,'start',sys.argv[1:]])+'\\n')
time.sleep(0.4)
with log.open('a') as f: f.write(json.dumps([label,'end'])+'\\n')
''')
        self.probe.chmod(0o755)
        self.log = Path(self.temp.name) / 'events'
        self.env = dict(os.environ, ORBITALIS_GRADLE_WORKERS='8')

    def launch(self, root, label, *args):
        return subprocess.Popen([sys.executable, str(RUNNER), '--root', str(root), str(self.probe),
                                 *args, str(self.log), label], env=self.env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def test_worktrees_share_slot_and_keep_explicit_limits(self):
        first = self.launch(self.root, 'first')
        deadline = time.monotonic() + 5
        while not self.log.exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertTrue(self.log.exists())
        second = self.launch(self.linked, 'second', '--max-workers=2')
        first.communicate(timeout=10)
        self.assertEqual(first.returncode, 0)
        _, stderr = second.communicate(timeout=10)
        self.assertEqual(second.returncode, 0)
        self.assertIn('Waiting', stderr)
        rows = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual([(r[0], r[1]) for r in rows],
                         [('first', 'start'), ('first', 'end'), ('second', 'start'), ('second', 'end')])
        self.assertEqual(rows[2][2][:1], ['--max-workers=2'])
        self.assertEqual(rows[0][2][:1], ['--max-workers=8'])

    def test_failure_releases_slot_and_preserves_status(self):
        failure = Path(self.temp.name) / 'failure'
        failure.write_text('#!/bin/sh\nexit 7\n')
        failure.chmod(0o755)
        result = subprocess.run([sys.executable, str(RUNNER), '--root', str(self.root), str(failure)],
                                env=self.env, capture_output=True)
        self.assertEqual(result.returncode, 7)
        process = self.launch(self.linked, 'after')
        process.communicate(timeout=10)
        self.assertEqual(process.returncode, 0)

    def test_invalid_limit_does_not_start_build(self):
        self.env['ORBITALIS_GRADLE_WORKERS'] = '0'
        process = self.launch(self.root, 'invalid')
        _, stderr = process.communicate(timeout=10)
        self.assertEqual(process.returncode, 1)
        self.assertIn('positive integer', stderr)
        self.assertFalse(self.log.exists())


if __name__ == '__main__':
    unittest.main()

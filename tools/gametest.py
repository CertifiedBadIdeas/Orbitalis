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

"""Own disposable native test runs; never prune pre-existing or failed worlds."""
import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import time


MARKER = '.orbitalis-gametest.json'


def run_path(root, run_id):
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', run_id):
        raise ValueError('Invalid GameTest run identifier')
    root = Path(root).absolute()
    if root.resolve() != root:
        raise ValueError('GameTest root must not traverse symlinks')
    path = root / run_id
    if path.is_symlink():
        raise ValueError('GameTest run must not be a symlink')
    return path


def prepare(root, run_id):
    path = run_path(root, run_id)
    # Loom may provision an empty working directory while configuring run metadata.
    path.mkdir(parents=True, exist_ok=True)
    if any(path.iterdir()):
        raise FileExistsError(f'Refusing to adopt a populated GameTest directory: {path}')
    with (path / MARKER).open('x') as marker:
        json.dump({'run': run_id, 'started': time.time()}, marker)
    return path


def world_metrics(world):
    dimensions = {}
    # Native region files use a minimum sector per chunk even for tiny air payloads.
    for region in sorted(world.rglob('r.*.*.mca')):
        if region.parent.name != 'region':
            continue
        key = str(region.parent.relative_to(world))
        entry = dimensions.setdefault(key, {'files': 0, 'bytes': 0, 'chunks': 0, 'short_headers': 0})
        entry['files'] += 1
        entry['bytes'] += region.stat().st_size
        with region.open('rb') as stream:
            header = stream.read(4096)
        if len(header) != 4096:
            entry['short_headers'] += 1
        else:
            entry['chunks'] += sum(offset != 0 for offset in struct.unpack('>1024I', header))
    return dimensions


def finish(root, run_id, success=False, report=None):
    path = run_path(root, run_id)
    marker = path / MARKER
    if not marker.exists():
        return None  # Gradle may finalize a run whose dependencies failed before prepare.
    ownership = json.loads(marker.read_text())
    if ownership.get('run') != run_id:
        raise ValueError('GameTest ownership marker mismatch')
    if ownership.get('finished'):
        raise ValueError('GameTest run already finalized')
    # Fail closed even on nested links: metrics and cleanup must remain inside this owned run.
    if any(item.is_symlink() for item in path.rglob('*')):
        raise ValueError('Refusing GameTest cleanup/metrics through symlinks')
    metrics = {'run': run_id, 'success': success, 'elapsed_seconds': time.time() - ownership['started'],
               'regions': world_metrics(path / 'world')}
    stopping = path / 'server-stopping.json'
    if stopping.is_file():
        metrics['server'] = json.loads(stopping.read_text())
        metrics['shutdown_seconds'] = time.time() - metrics['server']['stopping_epoch_millis'] / 1000
    if report and Path(report).is_file() and Path(report).stat().st_mtime >= ownership['started']:
        shutil.copyfile(report, path / 'results.xml')
    (path / 'metrics.json').write_text(json.dumps(metrics, indent=2) + '\n')
    # Called only after normal native exit AND the Gradle report assertions have passed.
    if success:
        for disposable in ('world', 'mods', '.sable'):
            directory = path / disposable
            if directory.exists():
                shutil.rmtree(directory)
    ownership['finished'] = True
    marker.write_text(json.dumps(ownership))
    print(json.dumps(metrics, sort_keys=True))
    return metrics


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('prepare', 'finish'))
    parser.add_argument('root')
    parser.add_argument('run_id')
    parser.add_argument('--success', action='store_true')
    parser.add_argument('--report')
    args = parser.parse_args()
    if args.operation == 'prepare':
        prepare(args.root, args.run_id)
    else:
        finish(args.root, args.run_id, args.success, args.report)

#!/usr/bin/env python3
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

"""Verify development tooling, source ownership and the actual distributable archive."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONSUMER_MOD_IDS = frozenset(('create', 'createpropulsion', 'aeronautics_bundled', 'aeronautics',
                              'simulated', 'offroad', 'flywheel', 'ponder', 'registrate', 'orbitalis_flight_consumer'))
EXTERNAL_CLASS_PREFIXES = ('net/minecraft/', 'net/neoforged/', 'dev/ryanhcode/', 'foundry/veil/',
                           'com/simibubi/', 'dev/propulsionteam/', 'dev/createpropulsionsimulated/',
                           'dev/eriksonn/', 'dev/simulated_team/', 'dev/engine_room/',
                           'net/createmod/', 'com/tterrag/registrate/', 'org/cbi/orbitalis/consumer/')
KOTLIN_LEGAL_FILES = ('LICENSE.txt', 'COPYRIGHT.txt', 'PROVENANCE.md',
                      'third_party/gwt_license.txt', 'third_party/guava_license.txt',
                      'third_party/threetenbp_license.txt', 'third_party/boost_LICENSE.txt')


def tooling():
    for directory in ('gradle', 'worktrees', 'tests'):
        subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', f'tools/{directory}',
                        '-p', 'test_*.py', '-v'], cwd=ROOT, check=True)


def architecture():
    errors = []
    for path in (ROOT / 'modules').rglob('*'):
        if path.suffix not in ('.kt', '.java') or 'build' in path.relative_to(ROOT).parts:
            continue
        text = path.read_text()
        relative = path.relative_to(ROOT).as_posix()
        imports = re.findall(r'^import\s+(?:static\s+)?([\w.]+)', text, re.M)
        if relative.startswith('modules/common/') and any(
                item.startswith(('net.minecraft.', 'net.neoforged.', 'com.mojang.', 'dev.ryanhcode.', 'foundry.veil.')) for item in imports):
            errors.append(f'{relative}: Minecraft/NeoForge/Sable imports in independent code')
        if relative.startswith('modules/minecraft/shared/common/') and any(
                item.startswith('net.neoforged.') for item in imports):
            errors.append(f'{relative}: NeoForge imports in shared/common')
        if re.search(r'^package\s+net\.(?:minecraft|neoforged)(?:\.|\s|;)', text, re.M):
            errors.append(f'{relative}: project code in an upstream package')
        if '/shared/' in relative and re.search(r'^import\s+org\.cbi\.orbitalis\.compat\.v[\w.]+', text, re.M):
            errors.append(f'{relative}: shared code imports a version implementation')
    for path in ('LICENSE.md', 'NOTICE', 'licenses/README.md'):
        if not (ROOT / path).is_file():
            errors.append(f'Missing {path}')
    if errors:
        raise ValueError('\n'.join(errors))
    print('Architecture source boundaries and license files verified.')


def jar(path, version):
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        if len(entries) != len(set(entries)):
            raise ValueError('Duplicate archive entries')
        required = ('META-INF/neoforge.mods.toml', 'META-INF/LICENSE.md', 'META-INF/NOTICE',
                    'org/cbi/orbitalis/core/Orbitalis.class',
                    'org/cbi/orbitalis/neoforge/OrbitalisMod.class',
                    'data/orbitalis/dimension/planet_lab.json',
                    'data/orbitalis/dimension/planet_lab.json.license',
                    'data/orbitalis/dimension_physics/planet_lab.json',
                    'data/orbitalis/dimension_physics/planet_lab.json.license',
                    'data/orbitalis/dimension/flight_test.json',
                    'data/orbitalis/dimension/flight_test.json.license',
                    'data/orbitalis/dimension_physics/flight_test.json',
                    'data/orbitalis/dimension_physics/flight_test.json.license',
                    'data/orbitalis/dimension/orbit_test.json',
                    'data/orbitalis/dimension/orbit_test.json.license',
                    'data/orbitalis/dimension_physics/orbit_test.json',
                    'data/orbitalis/dimension_physics/orbit_test.json.license',
                    'data/orbitalis/dimension/space_test.json',
                    'data/orbitalis/dimension/space_test.json.license',
                    'data/orbitalis/dimension_physics/space_test.json',
                    'data/orbitalis/dimension_physics/space_test.json.license')
        for name in required:
            if entries.count(name) != 1:
                raise ValueError(f'Missing or duplicated {name}')
        for legal in KOTLIN_LEGAL_FILES:
            name = 'META-INF/licenses/kotlin-stdlib/' + legal
            if name not in entries:
                raise ValueError(f'Kotlin runtime license missing: {name}')
        metadata = archive.read(required[0]).decode()
        if '${' in metadata:
            raise ValueError('Unexpanded metadata')
        parsed = tomllib.loads(metadata)
        mod, = parsed['mods']
        if mod['modId'] != 'orbitalis' or mod['version'] != version or parsed['license'] != 'Apache-2.0':
            raise ValueError('Wrong mod identity, version or license')
        dependencies = {item['modId']: item for item in parsed['dependencies']['orbitalis']}
        if CONSUMER_MOD_IDS.intersection(dependencies):
            raise ValueError('Consumer-only dependency leaked into production metadata')
        if dependencies['minecraft']['versionRange'] != '[1.21.1,1.21.2)':
            raise ValueError('Wrong Minecraft compatibility range')
        if any(name.startswith(EXTERNAL_CLASS_PREFIXES) or
               ('GameTest' in name and name.endswith('.class')) for name in entries):
            raise ValueError('Upstream or GameTest classes leaked into production')
        if any(name in entries for name in ('data/orbitalis/structure/lab_empty.nbt',
                                            'data/minecraft/worldgen/world_preset/flat.json')):
            raise ValueError('GameTest world resources leaked into production')
        if any(name.startswith('data/orbitalis_flight_test/') for name in entries):
            raise ValueError('Consumer-only resources leaked into production')
        # Enforce the bytecode ceiling for the current Java 21 production target.
        for name in entries:
            if name.endswith('.class') and name.startswith('org/cbi/orbitalis/'):
                data = archive.read(name)
                if data[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(data[6:8], 'big') > 65:
                    raise ValueError(f'Not Java 21 compatible: {name}')
        # Loom nests the unmodified stdlib; its legal files are packaged beside it.
        import io
        nested = [name for name in entries if name.endswith('.jar')]
        runtimes = []
        for name in nested:
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as inner:
                if any(entry.startswith(EXTERNAL_CLASS_PREFIXES) for entry in inner.namelist()):
                    raise ValueError(f'Upstream implementation leaked into nested archive: {name}')
                if 'kotlin/jvm/internal/Intrinsics.class' in inner.namelist():
                    runtimes.append(name)
        if not runtimes:
            raise ValueError('Kotlin runtime missing from production archive')
    print(f'Production archive verified: {path}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('tooling')
    commands.add_parser('architecture')
    package = commands.add_parser('jar')
    package.add_argument('path', type=Path)
    package.add_argument('version')
    arguments = parser.parse_args()
    try:
        if arguments.command == 'jar':
            jar(arguments.path, arguments.version)
        else:
            globals()[arguments.command]()
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

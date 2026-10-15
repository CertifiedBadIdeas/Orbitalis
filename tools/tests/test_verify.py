# The Orbitalis Developers
#
# Copyright 2026 The Orbitalis Developers
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

"""Distribution contract tests: fail closed on missing legal files/runtime or wrong bytecode."""
import io
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify


class ProductionArchiveTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.archive = Path(self.directory.name) / 'orbitalis.jar'
        self.files = {
            'META-INF/neoforge.mods.toml': '''license="Apache-2.0"
[[mods]]
modId="orbitalis"
version="0.1.0-SNAPSHOT"
[[dependencies.orbitalis]]
modId="minecraft"
versionRange="[1.21.1,1.21.2)"
'''.encode(),
            'META-INF/LICENSE.md': b'Apache-2.0 fixture',
            'META-INF/NOTICE': b'upstream fixture',
            'org/cbi/orbitalis/core/Orbitalis.class': self.bytecode(65),
            'org/cbi/orbitalis/neoforge/OrbitalisMod.class': self.bytecode(65),
        }
        for legal in verify.KOTLIN_LEGAL_FILES:
            self.files['META-INF/licenses/kotlin-stdlib/' + legal] = b'legal fixture'
        runtime = io.BytesIO()
        with zipfile.ZipFile(runtime, 'w') as nested:
            nested.writestr('kotlin/jvm/internal/Intrinsics.class', self.bytecode(52))
            nested.writestr('META-INF/LICENSE', 'Kotlin license fixture')
        self.files['META-INF/jars/kotlin-stdlib.jar'] = runtime.getvalue()

    @staticmethod
    def bytecode(major):
        # Only the class header is relevant to the packaging ceiling check.
        return b'\xca\xfe\xba\xbe\x00\x00' + major.to_bytes(2, 'big')

    def run_verifier(self):
        with zipfile.ZipFile(self.archive, 'w') as archive:
            for name, data in self.files.items():
                archive.writestr(name, data)
        verify.jar(self.archive, '0.1.0-SNAPSHOT')

    def test_accepts_complete_java21_distribution_contract(self):
        self.run_verifier()

    def test_missing_legal_notice_is_rejected(self):
        del self.files['META-INF/NOTICE']
        with self.assertRaisesRegex(ValueError, 'NOTICE'):
            self.run_verifier()

    def test_missing_runtime_third_party_license_is_rejected(self):
        del self.files['META-INF/licenses/kotlin-stdlib/third_party/threetenbp_license.txt']
        with self.assertRaisesRegex(ValueError, 'runtime license missing'):
            self.run_verifier()

    def test_java25_project_class_is_rejected(self):
        self.files['org/cbi/orbitalis/core/Orbitalis.class'] = self.bytecode(69)
        with self.assertRaisesRegex(ValueError, 'Java 21'):
            self.run_verifier()

    def test_missing_kotlin_runtime_is_rejected(self):
        del self.files['META-INF/jars/kotlin-stdlib.jar']
        with self.assertRaisesRegex(ValueError, 'runtime missing'):
            self.run_verifier()

    def test_bundled_minecraft_classes_are_rejected(self):
        self.files['net/minecraft/Example.class'] = self.bytecode(65)
        with self.assertRaisesRegex(ValueError, 'leaked'):
            self.run_verifier()


if __name__ == '__main__':
    unittest.main()

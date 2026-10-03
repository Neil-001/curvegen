import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from zipfile import ZipFile


SCRIPTS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("release_jars", SCRIPTS / "release-jars.py")
release_jars = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_jars)


class ReleaseJarsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def jar(self, path, loader="fabric", version="1.0.0", minecraft=">=26.1 <=26.1.2"):
        path = self.root / path
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(path, "w") as archive:
            if loader == "fabric":
                archive.writestr("fabric.mod.json", json.dumps(dict(
                    id="curvegen", version=version, depends=dict(minecraft=minecraft))))
            else:
                archive.writestr("META-INF/neoforge.mods.toml", f'''
[[mods]]
modId = "curvegen"
version = "{version}"
[[dependencies.curvegen]]
modId = "minecraft"
versionRange = "[26.1,26.1.2]"
''')
        return path

    def dual(self):
        self.jar("fabric/build/libs/curvegen-fabric-1.0.0.jar")
        self.jar("neoforge/build/libs/curvegen-neoforge-1.0.0.jar", "neoforge")

    def collect(self):
        return release_jars.collect("1.0.0", "26.1", "25", self.root)

    def test_both_loaders_have_distinct_versions_and_metadata(self):
        self.dual()
        self.jar("fabric/build/libs/curvegen-fabric-1.0.0-sources.jar", version="wrong")
        self.jar("fabric/build/libs/curvegen-fabric-1.0.0-dev.jar", version="wrong")
        jars = self.collect()
        self.assertEqual([jar["version"] for jar in jars],
                         ["1.0.0+mc26.1-fabric", "1.0.0+mc26.1-neoforge"])
        self.assertEqual([jar["loader"] for jar in jars], ["fabric", "neoforge"])
        self.assertEqual([jar["java"] for jar in jars], ["25", "25"])
        self.assertEqual([jar["minecraft"] for jar in jars],
                         [">=26.1 <=26.1.2", "[26.1,26.1.2]"])
        self.assertEqual(len(list((self.root / "dist").glob("*.jar"))), 2)

    def test_stonecutter_preserves_versions_and_collects_copies_once(self):
        for mc in ["1.21", "1.21.11"]:
            source = self.jar(f"versions/{mc}/build/libs/curvegen-{mc}.jar",
                              version=f"1.0.0+mc{mc}", minecraft=mc)
            target = self.root / "build/libs" / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        jars = release_jars.collect("1.0.0", "1.21.x", "21", self.root)
        self.assertEqual({jar["version"] for jar in jars}, {"1.0.0+mc1.21", "1.0.0+mc1.21.11"})
        self.assertTrue(all(jar["java"] == "21" and jar["loader"] == "fabric" for jar in jars))

    def test_single_loader_root_and_stonecutter_fallback(self):
        for path in ["build/libs/mod.jar", "versions/1.21/build/libs/mod.jar"]:
            with self.subTest(path=path):
                source = self.jar(path)
                self.assertEqual(len(self.collect()), 1)
                source.unlink()

    def test_wrong_version_fails_before_copying(self):
        self.dual()
        self.jar("neoforge/build/libs/curvegen-neoforge-1.0.0.jar", "neoforge", "1.0.1")
        with self.assertRaisesRegex(ValueError, "expected 1.0.0"):
            self.collect()
        self.assertFalse((self.root / "dist").exists())

    def test_missing_loader_fails(self):
        self.dual()
        (self.root / "neoforge/build/libs/curvegen-neoforge-1.0.0.jar").unlink()
        with self.assertRaisesRegex(ValueError, "both Fabric and NeoForge"):
            self.collect()

    def test_wrong_loader_fails(self):
        self.dual()
        self.jar("neoforge/build/libs/curvegen-neoforge-1.0.0.jar")
        with self.assertRaisesRegex(ValueError, "wrong loader"):
            self.collect()

    def test_conflicting_copies_and_duplicate_versions_fail(self):
        self.jar("build/libs/mod.jar")
        self.jar("versions/26.1/build/libs/mod.jar", version="1.0.1")
        with self.assertRaisesRegex(ValueError, "same filename"):
            self.collect()
        (self.root / "versions/26.1/build/libs/mod.jar").unlink()
        self.jar("build/libs/other.jar")
        with self.assertRaisesRegex(ValueError, "Multiple jars"):
            self.collect()

    def test_no_jars_fails(self):
        with self.assertRaisesRegex(ValueError, "no jars"):
            self.collect()


class ReleasePlanTest(unittest.TestCase):
    def test_mixed_branches_versions_and_tag_skip(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL, text=True)

            git("init", "-q")
            git("config", "user.name", "Release test")
            git("config", "user.email", "test@example.com")
            (root / "stonecutter.properties.toml").write_text('mod.version = "1.0.0"\n')
            git("add", ".")
            git("commit", "-qm", "Legacy")
            git("update-ref", "refs/remotes/origin/1.21.x/stable", "HEAD")
            (root / "stonecutter.properties.toml").unlink()
            (root / "gradle.properties").write_text("mod_version=1.1.0-beta.1\n")
            git("add", "-A")
            git("commit", "-qm", "Dual loader")
            git("update-ref", "refs/remotes/origin/26.1/stable", "HEAD")
            git("update-ref", "refs/remotes/origin/main", "HEAD")

            def plan(branches):
                return json.loads(subprocess.check_output(
                    [str(SCRIPTS / "release-plan.sh"), branches], cwd=root, text=True,
                    stderr=subprocess.DEVNULL))

            result = plan("all")
            self.assertEqual([item["tag"] for item in result],
                             ["1.0.0+mc1.21.x", "1.1.0-beta.1+mc26.1"])
            self.assertEqual(result[1]["sha"], git("rev-parse", "HEAD").strip())
            git("tag", "1.0.0+mc1.21.x")
            self.assertEqual(plan("all"), [result[1]])
            self.assertEqual(plan("26.1/stable,26.1/stable"), [result[1]])
            with self.assertRaises(subprocess.CalledProcessError):
                plan("main")


if __name__ == "__main__":
    unittest.main()

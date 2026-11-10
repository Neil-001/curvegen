"""Checks the gallery README parser against the real README and a few shapes it must reject, and the
CurseForge page's HTML against the markdown shapes the descriptions use."""
import importlib.util
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


listing = load("listing_modrinth", HERE / "listing-modrinth.py")
try:
    import markdown as _markdown  # noqa: F401
    curseforge = load("curseforge", HERE.parents[1] / "listing" / "art" / "curseforge.py")
except ImportError:
    curseforge = None


class GalleryReadme(unittest.TestCase):
    def test_the_real_readme_lists_every_picture_once(self):
        readme = (HERE.parents[1] / "listing" / "gallery" / "README.md").read_text(encoding="utf-8")
        entries = listing.gallery_entries(readme)
        files = [file for file, _, _ in entries]
        self.assertEqual(len(files), len(set(files)))
        for file, title, description in entries:
            self.assertTrue((HERE.parents[1] / "listing" / "gallery" / file).is_file(), file)
            self.assertTrue(title and description, file)

    def test_a_section_parses_into_file_title_and_description(self):
        readme = "# Gallery\n\nIntro.\n\n## 1. Match a colour\n\n`01-match.jpg`\n\nFirst line.\n\nSecond paragraph.\n"
        self.assertEqual(listing.gallery_entries(readme), [("01-match.jpg", "Match a colour", "First line. Second paragraph.")])

    def test_a_section_without_a_file_is_refused(self):
        with self.assertRaises(ValueError):
            listing.gallery_entries("## 1. Lost\n\nNo file here.\n")

    def test_a_long_description_is_refused(self):
        readme = "## 1. Long\n\n`a.jpg`\n\n" + "x" * 256 + "\n"
        with self.assertRaises(ValueError):
            listing.gallery_entries(readme)


@unittest.skipUnless(curseforge, "needs the markdown package")
class CurseForgePage(unittest.TestCase):
    def test_a_centred_block_becomes_centred_paragraphs(self):
        html = curseforge.render("<center>\n\nOne.\n\nTwo.\n\n</center>\n\nThree.\n")
        self.assertEqual(html, f"{curseforge.CENTRE}One.</p>\n{curseforge.CENTRE}Two.</p>\n<p>Three.</p>\n")

    def test_an_item_indented_by_two_spaces_is_nested(self):
        html = curseforge.render("- **Outer**\n  - Inner.\n")
        self.assertEqual(html.replace("\n", ""), "<ul><li><strong>Outer</strong><ul><li>Inner.</li></ul></li></ul>")

    def test_the_fabric_api_link_and_the_badges_are_curseforges(self):
        badge = "https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api"
        html = curseforge.render(f'<a href="https://modrinth.com/mod/fabric-api"><img src="{badge}_vector.svg"></a>\n\n'
                                 "[Sodium](https://modrinth.com/mod/sodium) ![x](https://example.com/a_vector.svg)\n")
        self.assertIn(f'<a href="https://www.curseforge.com/minecraft/mc-mods/fabric-api"><img src="{badge}_64h.png"></a>', html)
        self.assertIn("https://modrinth.com/mod/sodium", html)
        self.assertIn("https://example.com/a_vector.svg", html)

    def test_the_committed_page_is_the_description_rendered(self):
        self.assertEqual(curseforge.TARGET.read_text(encoding="utf-8"),
                         curseforge.render(curseforge.SOURCE.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Puts the listing on the Modrinth project page: the description, the gallery and the icon.

    MODRINTH_TOKEN=… MODRINTH_PROJECT_ID=… python3 .github/scripts/listing-modrinth.py [--replace] [--dry-run]

The description is listing/description.md as it is. The gallery is the pictures listing/gallery/README.md lists,
with its titles and descriptions, in its order; a picture whose title the project already shows is left alone
unless --replace is given, which removes and uploads it again. The icon is listing/media/icon.png, and is left
alone when the listing has no such file. --dry-run says what would change and changes nothing. CurseForge has no API for any of this, so its page is done by hand from
listing/curseforge.html.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LISTING = ROOT / "listing"
API = "https://api.modrinth.com/v2"
# Modrinth asks for a user agent that says who is calling. In the workflow that is the repository.
REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "")
AGENT = f"{REPOSITORY} (listing upload; github.com/{REPOSITORY})" if REPOSITORY else "listing upload"
TITLE_MAX, DESCRIPTION_MAX = 64, 255


def gallery_entries(readme):
    """(file name, title, description) for each picture the gallery README lists, in order."""
    entries = []
    for section in re.split(r"^## ", readme, flags=re.M)[1:]:
        lines = section.strip("\n").split("\n")
        title = re.sub(r"^\d+\.\s*", "", lines[0]).strip()
        body = "\n".join(lines[1:]).strip()
        file = re.search(r"^`([^`]+)`\s*$", body, flags=re.M)
        if not file:
            raise ValueError(f"gallery section {title!r} names no file in backticks")
        description = " ".join(p.strip() for p in body[file.end():].strip().split("\n\n") if p.strip())
        if len(title) > TITLE_MAX or len(description) > DESCRIPTION_MAX:
            raise ValueError(f"gallery section {title!r}: title over {TITLE_MAX} or description over {DESCRIPTION_MAX} characters")
        entries.append((file.group(1), title, description))
    return entries


class Modrinth:
    def __init__(self, token, project, dry_run):
        self.token, self.project, self.dry_run = token, project, dry_run

    def request(self, method, path, body=None, content_type=None, query=None):
        url = f"{API}{path}" + (("?" + urllib.parse.urlencode(query)) if query else "")
        headers = {"Authorization": self.token, "User-Agent": AGENT}
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as e:
            sys.exit(f"{method} {path}: HTTP {e.code}: {e.read().decode(errors='replace')[:500]}")

    def get_project(self):
        return self.request("GET", f"/project/{self.project}")

    def set_description(self, markdown):
        if self.dry_run:
            print(f"would set the description, {len(markdown)} characters")
            return
        self.request("PATCH", f"/project/{self.project}", json.dumps({"body": markdown}).encode(), "application/json")
        print(f"description set, {len(markdown)} characters")

    def set_icon(self, path):
        if self.dry_run:
            print(f"would set the icon from {path.name}")
            return
        self.request("PATCH", f"/project/{self.project}/icon", path.read_bytes(), "image/png", {"ext": "png"})
        print(f"icon set from {path.name}")

    def delete_gallery_image(self, url):
        if self.dry_run:
            print(f"would remove the gallery picture at {url}")
            return
        self.request("DELETE", f"/project/{self.project}/gallery", query={"url": url})

    def add_gallery_image(self, path, title, description, ordering):
        if self.dry_run:
            print(f"would upload {path.name} as {title!r} at position {ordering}")
            return
        ext = path.suffix.lstrip(".").lower()
        mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}[ext]
        self.request("POST", f"/project/{self.project}/gallery", path.read_bytes(), mime,
                     {"ext": ext, "featured": "false", "title": title, "description": description, "ordering": ordering})
        print(f"uploaded {path.name} as {title!r} at position {ordering}")


def main(argv):
    token, project = os.environ.get("MODRINTH_TOKEN"), os.environ.get("MODRINTH_PROJECT_ID")
    if not token or not project:
        sys.exit("MODRINTH_TOKEN and MODRINTH_PROJECT_ID must be set")
    replace, dry_run = "--replace" in argv, "--dry-run" in argv
    api = Modrinth(token, project, dry_run)

    entries = gallery_entries((LISTING / "gallery" / "README.md").read_text(encoding="utf-8"))
    for file, _, _ in entries:
        if not (LISTING / "gallery" / file).is_file():
            sys.exit(f"listing/gallery/{file} is missing")

    before = api.get_project()
    print(f"project {before['title']!r} ({before['slug']}), status {before['status']}, {len(before['gallery'])} gallery pictures now")

    api.set_description((LISTING / "description.md").read_text(encoding="utf-8"))
    icon = LISTING / "media" / "icon.png"
    if icon.is_file():
        api.set_icon(icon)
    else:
        print("no listing/media/icon.png, so the icon is left alone")

    shown = {image["title"]: image for image in before["gallery"] if image.get("title")}
    for ordering, (file, title, description) in enumerate(entries):
        if title in shown:
            if not replace:
                print(f"kept {title!r}, already on the page")
                continue
            api.delete_gallery_image(shown[title]["url"])
        api.add_gallery_image(LISTING / "gallery" / file, title, description, ordering)

    if not dry_run:
        after = api.get_project()
        print(f"{len(after['gallery'])} gallery pictures now: " + ", ".join(repr(i.get("title")) for i in sorted(after["gallery"], key=lambda i: i.get("ordering", 0))))


if __name__ == "__main__":
    main(sys.argv[1:])

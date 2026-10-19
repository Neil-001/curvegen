#!/usr/bin/env bash
# Prints the "Full Changelog" line comparing a release with its line's previous one, or nothing
# for a line's first release.
#
#   release-compare.sh <line> <tag>
#
# The previous release is the nearest tag ending in exactly "+mc<line>", so line 1.21.1 never
# picks up a 1.21.11 tag. Needs the tags fetched, and GITHUB_SERVER_URL and GITHUB_REPOSITORY.
set -euo pipefail

line=$1
tag=$2
if previous=$(git describe --tags --abbrev=0 --match "*+mc$line" 2>/dev/null); then
	printf '\n**Full Changelog**: %s/%s/compare/%s...%s\n' "$GITHUB_SERVER_URL" "$GITHUB_REPOSITORY" "$previous" "$tag"
fi

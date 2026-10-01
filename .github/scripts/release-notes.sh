#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version, without its "# <version>" heading.
#
#   release-notes.sh <version> [changelog]
#
# Fails if the section is missing or empty.
set -euo pipefail

version=$1
file=${2:-CHANGELOG.md}

[ -f "$file" ] || { echo "::error::$file not found." >&2; exit 1; }

notes=$(awk -v heading="# $version" '
	$0 == heading { found = 1; next }
	found && /^# / { exit }
	found { print }
' "$file" | sed '/./,$!d')   # sed drops leading blank lines, $(...) drops trailing ones

[ -n "$notes" ] || { echo "::error::$file has no \"# $version\" section. Add one before releasing." >&2; exit 1; }
echo "$notes"

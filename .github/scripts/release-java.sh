#!/usr/bin/env bash
# Prints the Java level a branch compiles for, which is the JDK it builds with.
#
#   release-java.sh [checkout]
#
# Reads "options.release = N" from build.gradle (the two-loader branches, as "it.options.release")
# or build.gradle.kts (the Stonecutter branch). Fails if neither states one, or they state several.
set -euo pipefail

root=${1:-.}
# sed skips a missing file and still reads the other.
levels=$(sed -nE 's#^\s*(it\.)?options\.release\s*=\s*([0-9]+)\s*(//.*)?$#\2#p' \
	"$root/build.gradle" "$root/build.gradle.kts" 2>/dev/null | sort -u || true)

case $(wc -w <<< "$levels") in
	1) echo "$levels" ;;
	0) echo "::error::Neither build.gradle nor build.gradle.kts sets options.release, so the Java level is unknown." >&2; exit 1 ;;
	*) echo "::error::The build files set more than one options.release:" $levels >&2; exit 1 ;;
esac

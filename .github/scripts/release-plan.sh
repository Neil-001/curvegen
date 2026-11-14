#!/usr/bin/env bash
# Decides which branches the Release workflow publishes.
#
#   release-plan.sh <branches>
#
# <branches> is "all" (every */stable branch) or a comma-separated list of */stable branches and
# main. Prints a JSON array with one {branch, line, version, tag, sha} object per branch to release.
# A branch whose tag already exists is left out, so maintenance branches with no new version are
# skipped.
#
# main only releases in a repository that sets RELEASE_MAIN to true, one that publishes its newest
# line straight from main. Elsewhere main is promoted to a */stable branch first, and asking for
# main is an error. Left empty, <branches> is main where main releases and "all" elsewhere.
#
# A */stable branch's Minecraft line is its name without "/stable". main has no line in its name,
# so it takes minecraft_version from its gradle.properties.
#
# Needs a clone with every remote branch and tag fetched.
set -euo pipefail

main_releases=${RELEASE_MAIN:-}
requested=${1:-}
if [ -z "${requested// /}" ]; then
	if [ "$main_releases" = true ]; then requested=main; else requested=all; fi
fi

if [ "$requested" = all ]; then
	mapfile -t branches < <(git for-each-ref --format='%(refname:lstrip=3)' refs/remotes/origin | grep '/stable$' || true)
	[ ${#branches[@]} -gt 0 ] || { echo "::error::No */stable branches found." >&2; exit 1; }
else
	mapfile -t branches < <(tr ',' '\n' <<< "${requested// /}" | awk 'NF && !seen[$0]++')
fi

# The version is in stonecutter.properties.toml on multi-version branches and in gradle.properties on the rest.
read_version() {
	local v
	v=$(git show "origin/$1:stonecutter.properties.toml" 2>/dev/null | sed -n 's/^mod\.version *= *"\(.*\)" *$/\1/p' | head -n 1)
	[ -n "$v" ] || v=$(git show "origin/$1:gradle.properties" 2>/dev/null | sed -n 's/^mod_version *= *\(.*[^ ]\) *$/\1/p' | head -n 1)
	echo "$v"
}

plan='[]'
for branch in "${branches[@]}"; do
	case $branch in
		main)
			[ "$main_releases" = true ] || { echo "::error::main doesn't release here: the repository variable RELEASE_MAIN isn't true. Release its */stable branch." >&2; exit 1; } ;;
		*/stable) ;;
		*) echo "::error::$branch is not main or a */stable branch. Only those release." >&2; exit 1 ;;
	esac
	sha=$(git rev-parse -q --verify "refs/remotes/origin/$branch") || { echo "::error::Branch $branch does not exist." >&2; exit 1; }
	version=$(read_version "$branch")
	[ -n "$version" ] || { echo "::error::Could not read the mod version on $branch." >&2; exit 1; }

	if [ "$branch" = main ]; then
		line=$(git show "origin/main:gradle.properties" 2>/dev/null | sed -n 's/^minecraft_version *= *\(.*[^ ]\) *$/\1/p' | head -n 1)
		[ -n "$line" ] || { echo "::error::Could not read minecraft_version on main." >&2; exit 1; }
	else
		line=${branch%/stable}
	fi
	tag="$version+mc$line"
	if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
		echo "::notice::Skipping $branch: $tag is already released." >&2
		continue
	fi
	echo "Releasing $branch as $tag ($sha)." >&2
	plan=$(jq -c --arg branch "$branch" --arg line "$line" --arg version "$version" --arg tag "$tag" --arg sha "$sha" \
		'. + [{branch: $branch, line: $line, version: $version, tag: $tag, sha: $sha}]' <<< "$plan")
done

echo "$plan"

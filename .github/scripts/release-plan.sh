#!/usr/bin/env bash
# Decides which branches the Release workflow publishes.
#
#   release-plan.sh <branches>
#
# <branches> is "all" (every */stable branch) or a comma-separated list. Prints a JSON array with
# one {branch, line, version, tag, sha} object per branch to release. A branch whose tag already
# exists is left out, so maintenance branches with no new version are skipped.
#
# Needs a clone with every remote branch and tag fetched.
set -euo pipefail

requested=${1:-all}

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
		*/stable) ;;
		*) echo "::error::$branch is not a */stable branch. Only those release." >&2; exit 1 ;;
	esac
	sha=$(git rev-parse -q --verify "refs/remotes/origin/$branch") || { echo "::error::Branch $branch does not exist." >&2; exit 1; }
	version=$(read_version "$branch")
	[ -n "$version" ] || { echo "::error::Could not read the mod version on $branch." >&2; exit 1; }

	line=${branch%/stable}
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

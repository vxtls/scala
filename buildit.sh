#!/usr/bin/env bash
set -Eeuo pipefail

REPO_URL="https://github.com/vxtls/scala.git"
REMOTE="origin"
MARKER=".bootstrap-build-ok"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_DIR="${REPO_DIR:-$SCRIPT_DIR/scala}"
WORKTREE_ROOT="${WORKTREE_ROOT:-$SCRIPT_DIR}"
FORCE_VERIFY=0

GROUP1=(
  v1.0.0-b6-bootstrap
  v1.1.0-b4-bootstrap
  v1.1.0.5-bootstrap
  v1.1.1.2-bootstrap
  v1.1.1.3-bootstrap
  v1.2.0.0-bootstrap
  v1.2.0.2-bootstrap
  v1.3.0.0-bootstrap
  v1.3.0.4-bootstrap
  v1.3.0.6-bootstrap
)

GROUP2=(
  v1.3.0.9-bootstrap
  v1.3.0.11-bootstrap
  v1.3.0.12-bootstrap
)

GROUP3_MAKE=(
  v1.4.0.2-bootstrap
  v1.4.0+3-bootstrap
  v1.4.0+4-bootstrap
)

GROUP3_ANT=(
  way2scala-2.0-stage0-bootstrap
  way2scala-2.0-stage1-bootstrap
  way2scala-2.0-stage2-bootstrap
  way2scala-2.0-stage3-bootstrap
  way2scala-2.0-stage4-bootstrap
  way2scala-2.0-stage5-bootstrap
  way2scala-2.0-stage6-bootstrap
  way2scala-2.0-stage7-bootstrap
  way2scala-2.0-stage8-bootstrap
  way2scala-2.0-stage9-bootstrap
  way2scala-2.0-stage10-bootstrap
)

GROUP4=(
  v2.0.0-bootstrap
  v2.1.0-bootstrap
  v2.1.0+bridge-bootstrap
  v2.1.7-bootstrap
  v2.1.8-bootstrap
  v2.2.0-bootstrap
  v2.3.0-bootstrap
  v2.3.0+v2.3.3-bootstrap
  v2.3.0+v2.3.3-product-bootstrap
  v2.3.0+v2.3.3-pattern-bootstrap
  v2.3.3-bootstrap
  v2.3.3+v2.4.0-bootstrap
  v2.4.0-bootstrap
  v2.4.0+v2.5.1-product-bootstrap
  v2.4.0+v2.5.1-bootstrap
  v2.5.1-bootstrap
  v2.5.1+v2.6.1-lazy-bootstrap
  v2.5.1+v2.6.1-lazyfix-bootstrap
  v2.5.1+v2.6.1-lazyobjects-bootstrap
  v2.5.1+v2.6.1-lazygenerator-bootstrap
  v2.5.1+v2.6.1-lazytrait-bootstrap
  v2.5.1+v2.6.1-bootstrap
  v2.5.1+v2.6.1-lengthcompare-bootstrap
  v2.6.1-bootstrap
  v2.6.1+v2.7.1-javagen-bootstrap
  v2.6.1+v2.7.1-dualmode-bootstrap
  v2.6.1+v2.7.1-stringbuilder-bootstrap
  v2.6.1+v2.7.1-listops-bootstrap
  v2.7.1-bootstrap
  v2.7.1+v2.7.2-varargstarr-bootstrap
  v2.7.1+v2.7.2-preinit-bootstrap
  v2.7.2-bootstrap
  v2.7.2+v2.7.3-starr-bootstrap
  v2.7.3-bootstrap
  v2.7.4-bootstrap
  v2.7.5-bootstrap
  v2.7.6-bootstrap
  v2.7.6+v2.7.7-bootstrap
  v2.7.7-bootstrap
  v2.7.7+91d92ec-bootstrap
  v2.7.7+f9d6f83-bootstrap
  v2.7.7+020add4-bootstrap
  v2.7.7+a7ea097-bootstrap
  v2.7.7+3bbffde-bootstrap
  v2.7.7+67c3c68-bootstrap
  v2.7.7+0a80c26-bootstrap
  v2.7.7+9ce3682-bootstrap
  v2.7.7+14a631-bootstrap
  v2.7.7+8fb4f2-bootstrap
  v2.7.7+390ccac-bootstrap
  v2.7.7+a2166de-bootstrap
  v2.7.7+e41d30-bootstrap
  v2.7.7+3ee6b36-bootstrap
  v2.7.7+57e0d02-bootstrap
  v2.7.7+467cfb-bootstrap
  v2.7.7+21e5e4c-bootstrap
  v2.7.7+8a78d37-bootstrap
  v2.7.7+40707e0-bootstrap
  v2.7.7+22edfb2-bootstrap
  v2.7.7+522bf3a-bootstrap
  v2.7.7+7aa4764-bootstrap
  v2.7.7+27f573a-bootstrap
  v2.7.7+ced5ee3-bootstrap
  v2.7.7+d5b02c8-bootstrap
  v2.7.7+3a98614-bootstrap
  v2.7.7+84146e2-bootstrap
  v2.7.7+6a6a0ce-bootstrap
  v2.7.7+v2.8-diverged-bootstrap
  v2.7.7+55ce01a-bootstrap
  v2.7.7+2970a61-bootstrap
  v2.8-diverged+d92679d-bootstrap
  v2.8-diverged+42123a6-bootstrap
  v2.8-diverged+d6b43c4-bootstrap
  v2.7.7+839d0ee-bootstrap
  v2.8-diverged+fffe644-bootstrap
  v2.7.7+77c181c-bootstrap
  v2.8-diverged+36707c3-bootstrap
  v2.7.7+c641174-bootstrap
  v2.7.7+21284e8-bootstrap
  v2.7.7+c6411859-bootstrap
  v2.7.7+d76943f-bootstrap
  v2.7.7+710e1cb-bootstrap
  v2.8-diverged+f218c00-bootstrap
  v2.7.7+1a77a3b-bootstrap
  v2.8-diverged+457a672-bootstrap
  v2.7.7+8ff235f-bootstrap
  v2.7.7+f31eaf6-bootstrap
  v2.8-diverged+c5441dc-bootstrap
  v2.8-diverged+dd500f0-bootstrap
  v2.8-diverged+3891250-bootstrap
  v2.8.0-bootstrap
  v2.8-diverged+081b838-bootstrap
  v2.8-diverged+2c14b26-bootstrap
  v2.8-diverged+d367ae7-bootstrap
  v2.8-diverged+2b41733-bootstrap
  v2.8.1-bootstrap
  v2.8.2-bootstrap
  v2.8-diverged+32ca2f2-bootstrap
  v2.8-diverged+626e389-bootstrap
  v2.8-diverged+77eb8fe-bootstrap
  v2.8-diverged+ed54595-bootstrap
  v2.8-diverged+b6db478-bootstrap
  v2.8-diverged+8922c4e-bootstrap
  v2.8-diverged+60a88e0-bootstrap
  v2.8-diverged+6e15632-bootstrap
  v2.8-diverged+2bb5d58-bootstrap
  v2.8.2+1cbe06c-bootstrap
  v2.8-diverged+4253124-bootstrap
  v2.8-diverged+v2.9-diverged-bootstrap
  v2.9-diverged-bootstrap
  v2.9-diverged+067030c-bootstrap
  v2.9-diverged+1ca657a-bootstrap
  v2.9-diverged+825f369-bootstrap
  v2.9-diverged+a387c9f-bootstrap
  v2.9-diverged+f5faa91-bootstrap
  v2.9-diverged+15d4776-bootstrap
  v2.9.0-bootstrap
  v2.9.0+1-bootstrap
  v2.9.1-bootstrap
  v2.9.2-bootstrap
  v2.9.3-bootstrap
)

GROUP5=(
  v2.9.3+ff5619-bootstrap
  v2.9.3+55109d-bootstrap
  v2.9.3+3921e5b-bootstrap
  v2.10.0-M1-bootstrap
  v2.10.0-M1+99844e-bootstrap
  v2.10.0-M1+9b03e88-bootstrap
  v2.10.0-M1+bb23d76-bootstrap
  v2.10.0-M2-bootstrap
  v2.10.0-M2+382a16e-bootstrap
  v2.10.0-M2+acb2c85-bootstrap
  v2.10.0-M2+c297b97-bootstrap
  v2.10.0-M2+f9e8a8c-bootstrap
  v2.10.0-M2+f9e8a8c+814cf34-bootstrap
  v2.10.0-M2+814cf34-bootstrap
  v2.10.0-M2+814cf34+46d0d73-bootstrap
  v2.10.0-M2+46d0d73-bootstrap
  v2.10.0-M2+e497667-bootstrap
  v2.10.0-M2+6e7382b-bootstrap
  v2.10.0-M2+7f2624e-bootstrap
  v2.10.0-M2+0b3b12f-bootstrap
  v2.10.0-M2+5e4c47f-bootstrap
  v2.10.0-M2+2a3cf29-bootstrap
  v2.10.0-M2+17fa0b1-bootstrap
  v2.10.0-M2+e408aa9-bootstrap
  v2.10.0-M2+b37350b-bootstrap
  v2.10.0-M2+40c1fe3-bootstrap
  v2.10.0-M2+2093bcc-bootstrap
  v2.10.0-M2+ec9fb82-bootstrap
  v2.10.0-M2+be11c92-bootstrap
  v2.10.0-M2+7ec2126-bootstrap
  v2.10.0-M2+2b09d8c-bootstrap
  v2.10.0-M2+0f0144c-bootstrap
  v2.10.0-M2+f54e5c8-bootstrap
)

declare -A GROUP4_PREV=(
  [v2.8.2+1cbe06c-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.8-diverged+4253124-bootstrap]=v2.8.2+1cbe06c-bootstrap
  [v2.8-diverged+v2.9-diverged-bootstrap]=v2.8-diverged+4253124-bootstrap
  [v2.7.7+8ff235f-bootstrap]=v2.8-diverged+457a672-bootstrap
  [v2.7.7+f31eaf6-bootstrap]=v2.8-diverged+457a672-bootstrap
  [v2.9-diverged+067030c-bootstrap]=v2.8-diverged+v2.9-diverged-bootstrap
  [v2.9-diverged+1ca657a-bootstrap]=v2.9-diverged+067030c-bootstrap
  [v2.9-diverged+825f369-bootstrap]=v2.9-diverged+1ca657a-bootstrap
  [v2.9-diverged+a387c9f-bootstrap]=v2.9-diverged+825f369-bootstrap
  [v2.9-diverged+f5faa91-bootstrap]=v2.9-diverged+a387c9f-bootstrap
  [v2.9-diverged+15d4776-bootstrap]=v2.9-diverged+f5faa91-bootstrap
  [v2.9.0-bootstrap]=v2.9-diverged+15d4776-bootstrap
  [v2.9.0+1-bootstrap]=v2.9.0-bootstrap
  [v2.9.1-bootstrap]=v2.9.0+1-bootstrap
  [v2.9.2-bootstrap]=v2.9.1-bootstrap
  [v2.9.3-bootstrap]=v2.9.2-bootstrap
  [v2.8-diverged+32ca2f2-bootstrap]=v2.8-diverged+2b41733-bootstrap
)

declare -A GROUP4_BRIDGE_PREV=(
  [v2.8.1-bootstrap]=v2.8-diverged+d367ae7-bootstrap
)

declare -A GROUP4_JAVABOOTCLASSPATH_PREV=(
  [v2.8.2+1cbe06c-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.8-diverged+4253124-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.8-diverged+v2.9-diverged-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+067030c-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+1ca657a-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+825f369-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+a387c9f-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+f5faa91-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9-diverged+15d4776-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9.0-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9.0+1-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9.1-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9.2-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
  [v2.9.3-bootstrap]=v2.8-diverged+2bb5d58-bootstrap
)

declare -A GROUP5_PREV=(
  [v2.9.3+ff5619-bootstrap]=v2.9.3-bootstrap
  [v2.9.3+55109d-bootstrap]=v2.9.3+ff5619-bootstrap
  [v2.9.3+3921e5b-bootstrap]=v2.9.3+55109d-bootstrap
  [v2.10.0-M1-bootstrap]=v2.9.3+3921e5b-bootstrap
  [v2.10.0-M1+99844e-bootstrap]=v2.10.0-M1-bootstrap
  [v2.10.0-M1+9b03e88-bootstrap]=v2.10.0-M1+99844e-bootstrap
  [v2.10.0-M1+bb23d76-bootstrap]=v2.10.0-M1+9b03e88-bootstrap
  [v2.10.0-M2-bootstrap]=v2.10.0-M1+bb23d76-bootstrap
  [v2.10.0-M2+382a16e-bootstrap]=v2.10.0-M2-bootstrap
  [v2.10.0-M2+acb2c85-bootstrap]=v2.10.0-M2+382a16e-bootstrap
  [v2.10.0-M2+c297b97-bootstrap]=v2.10.0-M2+acb2c85-bootstrap
  [v2.10.0-M2+f9e8a8c-bootstrap]=v2.10.0-M2+c297b97-bootstrap
  [v2.10.0-M2+f9e8a8c+814cf34-bootstrap]=v2.10.0-M2+f9e8a8c-bootstrap
  [v2.10.0-M2+814cf34-bootstrap]=v2.10.0-M2+f9e8a8c+814cf34-bootstrap
  [v2.10.0-M2+814cf34+46d0d73-bootstrap]=v2.10.0-M2+814cf34-bootstrap
  [v2.10.0-M2+46d0d73-bootstrap]=v2.10.0-M2+814cf34+46d0d73-bootstrap
  [v2.10.0-M2+e497667-bootstrap]=v2.10.0-M2+46d0d73-bootstrap
  [v2.10.0-M2+6e7382b-bootstrap]=v2.10.0-M2+e497667-bootstrap
  [v2.10.0-M2+7f2624e-bootstrap]=v2.10.0-M2+6e7382b-bootstrap
  [v2.10.0-M2+0b3b12f-bootstrap]=v2.10.0-M2+7f2624e-bootstrap
  [v2.10.0-M2+5e4c47f-bootstrap]=v2.10.0-M2+0b3b12f-bootstrap
  [v2.10.0-M2+2a3cf29-bootstrap]=v2.10.0-M2+5e4c47f-bootstrap
  [v2.10.0-M2+17fa0b1-bootstrap]=v2.10.0-M2+2a3cf29-bootstrap
  [v2.10.0-M2+e408aa9-bootstrap]=v2.10.0-M2+17fa0b1-bootstrap
  [v2.10.0-M2+b37350b-bootstrap]=v2.10.0-M2+e408aa9-bootstrap
  [v2.10.0-M2+40c1fe3-bootstrap]=v2.10.0-M2+b37350b-bootstrap
  [v2.10.0-M2+2093bcc-bootstrap]=v2.10.0-M2+40c1fe3-bootstrap
  [v2.10.0-M2+ec9fb82-bootstrap]=v2.10.0-M2+2093bcc-bootstrap
  [v2.10.0-M2+be11c92-bootstrap]=v2.10.0-M2+ec9fb82-bootstrap
  [v2.10.0-M2+7ec2126-bootstrap]=v2.10.0-M2+be11c92-bootstrap
  [v2.10.0-M2+2b09d8c-bootstrap]=v2.10.0-M2+7ec2126-bootstrap
  [v2.10.0-M2+0f0144c-bootstrap]=v2.10.0-M2+2b09d8c-bootstrap
  [v2.10.0-M2+f54e5c8-bootstrap]=v2.10.0-M2+0f0144c-bootstrap
)

declare -A GROUP5_MODE=(
  [v2.9.3+ff5619-bootstrap]=build
  [v2.9.3+55109d-bootstrap]=build
  [v2.9.3+3921e5b-bootstrap]=build
  [v2.10.0-M1+99844e-bootstrap]=build
  [v2.10.0-M1+9b03e88-bootstrap]=build
  [v2.10.0-M1+bb23d76-bootstrap]=build
  [v2.10.0-M2+382a16e-bootstrap]=build
  [v2.10.0-M2+acb2c85-bootstrap]=build
  [v2.10.0-M2+c297b97-bootstrap]=build
  [v2.10.0-M2+f9e8a8c-bootstrap]=build
  [v2.10.0-M2+f9e8a8c+814cf34-bootstrap]=build
  [v2.10.0-M2+814cf34-bootstrap]=build
  [v2.10.0-M2+814cf34+46d0d73-bootstrap]=build
  [v2.10.0-M2+46d0d73-bootstrap]=build
  [v2.10.0-M2+e497667-bootstrap]=build
  [v2.10.0-M2+6e7382b-bootstrap]=build
  [v2.10.0-M2+7f2624e-bootstrap]=build
  [v2.10.0-M2+0b3b12f-bootstrap]=build
  [v2.10.0-M2+5e4c47f-bootstrap]=build
  [v2.10.0-M2+2a3cf29-bootstrap]=build
  [v2.10.0-M2+17fa0b1-bootstrap]=build
  [v2.10.0-M2+e408aa9-bootstrap]=build
  [v2.10.0-M2+b37350b-bootstrap]=build
  [v2.10.0-M2+40c1fe3-bootstrap]=build
  [v2.10.0-M2+2093bcc-bootstrap]=build
  [v2.10.0-M2+ec9fb82-bootstrap]=build
  [v2.10.0-M2+be11c92-bootstrap]=build
  [v2.10.0-M2+7ec2126-bootstrap]=build
  [v2.10.0-M2+2b09d8c-bootstrap]=build
  [v2.10.0-M2+0f0144c-bootstrap]=build
  [v2.10.0-M2+f54e5c8-bootstrap]=build
)

EXPECTED_ERROR2=(
  v1.0.0-b6-bootstrap
  v1.1.0-b4-bootstrap
)

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

run() {
  echo
  echo ">>> $*"
  "$@"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --force-verify)
        FORCE_VERIFY=1
        ;;
      *)
        fail "Unknown option: $1"
        ;;
    esac
    shift
  done
}

expects_error2() {
  local branch="$1"
  local expected

  for expected in "${EXPECTED_ERROR2[@]}"; do
    [[ "$branch" == "$expected" ]] && return 0
  done

  return 1
}

uses_bootstrap_home() {
  grep -q '^BOOTSTRAP_HOME[[:space:]]*[?:]*=' Makefile.config
}

absolute_path() {
  local path="$1"
  local dir
  local base

  if [[ -d "$path" ]]; then
    (cd "$path" && pwd -P)
  else
    dir="$(dirname "$path")"
    base="$(basename "$path")"
    [[ -d "$dir" ]] || fail "Path does not exist: $path"
    printf '%s/%s\n' "$(cd "$dir" && pwd -P)" "$base"
  fi
}

stage_path() {
  printf '%s/%s\n' "$WORKTREE_ROOT" "$1"
}

clean_make_tree() {
  run make distclean
}

ant_has_target() {
  local target="$1"

  LC_ALL=C tr '\n' ' ' < build.xml | grep -Eq "<target[^>]*name=\"$target\""
}

group4_ant_targets() {
  if ant_has_target clean.build; then
    printf '%s\n' clean.build build
  elif ant_has_target bridge.clean; then
    printf '%s\n' bridge.clean clean build
  else
    printf '%s\n' clean build
  fi
}

group4_starr_lib() {
  local prev="$1"

  case "$prev" in
    way2scala-2.0-stage10-bootstrap)
      absolute_path "$(stage_path "$prev")/build/lib/scala2-library.jar"
      ;;
    v2.0.0-bootstrap|v2.1.0-bootstrap|v2.1.0+bridge-bootstrap)
      absolute_path "$(stage_path "$prev")/build/quick/library"
      ;;
    v2.7.7+*-bootstrap|v2.8.*-bootstrap|v2.8-diverged*-bootstrap|v2.9*-bootstrap)
      absolute_path "$(stage_path "$prev")/build/pack/lib/scala-library.jar"
      ;;
    v2.7.2+v2.7.3-starr-bootstrap|v2.7.[3-9]*-bootstrap|v2.7.*+v2.7.*-bootstrap)
      absolute_path "$(stage_path "$prev")/build/quick/classes/library"
      ;;
    *)
      absolute_path "$(stage_path "$prev")/build/quick/lib/library"
      ;;
  esac
}

group4_starr_comp() {
  local prev="$1"

  case "$prev" in
    way2scala-2.0-stage10-bootstrap)
      absolute_path "$(stage_path "$prev")/build/lib/scala2-compiler.jar"
      ;;
    v2.0.0-bootstrap|v2.1.0-bootstrap|v2.1.0+bridge-bootstrap)
      absolute_path "$(stage_path "$prev")/build/quick/compiler"
      ;;
    v2.7.7+*-bootstrap|v2.8.*-bootstrap|v2.8-diverged*-bootstrap|v2.9*-bootstrap)
      absolute_path "$(stage_path "$prev")/build/pack/lib/scala-compiler.jar"
      ;;
    v2.7.2+v2.7.3-starr-bootstrap|v2.7.[3-9]*-bootstrap|v2.7.*+v2.7.*-bootstrap)
      absolute_path "$(stage_path "$prev")/build/quick/classes/compiler"
      ;;
    *)
      absolute_path "$(stage_path "$prev")/build/quick/lib/compiler"
      ;;
  esac
}

group4_fjbg_jar() {
  absolute_path "$(stage_path "way2scala-2.0-stage10-bootstrap")/distribs/unix/scala-2.0-stage10-bootstrap/share/scala/lib/fjbg.jar"
}

group4_uses_source_fjbg() {
  case "$1" in
    v2.7.7+8ff235f-bootstrap|v2.8.2+1cbe06c-bootstrap|v2.8-diverged+4253124-bootstrap|v2.8-diverged+v2.9-diverged-bootstrap|v2.9-diverged-bootstrap|v2.9-diverged+067030c-bootstrap|v2.9-diverged+1ca657a-bootstrap|v2.9-diverged+825f369-bootstrap|v2.9-diverged+a387c9f-bootstrap|v2.9-diverged+f5faa91-bootstrap|v2.9-diverged+15d4776-bootstrap|v2.9.0-bootstrap|v2.9.0+1-bootstrap|v2.9.1-bootstrap|v2.9.2-bootstrap|v2.9.3-bootstrap)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

run_build() {
  local branch="$1"
  local status
  shift

  echo
  echo ">>> $*"

  if "$@"; then
    return 0
  else
    status=$?
  fi

  if [[ "$status" -eq 2 ]] && expects_error2 "$branch"; then
    echo ">>> Expected exit code 2; treating as success: $branch"
    return 0
  fi

  return "$status"
}

ensure_tools() {
  command -v git >/dev/null 2>&1 || fail "git not found"
  command -v make >/dev/null 2>&1 || fail "make not found"
  command -v ant >/dev/null 2>&1 || fail "ant not found; add ant to PATH first"
}

ensure_repo() {
  if [[ -d "$REPO_DIR/.git" ]]; then
    echo ">>> $REPO_DIR/.git already exists; skipping clone"
  elif [[ -e "$REPO_DIR" ]]; then
    fail "$REPO_DIR exists, but it is not a Git repository"
  else
    run git clone "$REPO_URL" "$REPO_DIR"
  fi

  cd "$REPO_DIR"

  run git fetch "$REMOTE" \
    "+refs/heads/*:refs/remotes/$REMOTE/*" \
    --prune
}

already_built() {
  local branch="$1"
  local marker_commit
  local current_commit

  if [[ -f "$MARKER" ]]; then
    if [[ "$FORCE_VERIFY" -ne 1 ]]; then
      echo ">>> Success marker already exists; skipping build: $branch"
      echo ">>> marker: $(pwd)/$MARKER"
      return 0
    fi

    marker_commit="$(sed -n 's/^commit=//p' "$MARKER" | head -n 1)"
    current_commit="$(git rev-parse HEAD)"

    if [[ "$marker_commit" != "$current_commit" ]]; then
      echo ">>> Ignoring stale success marker: $branch"
      echo ">>> marker commit: ${marker_commit:-<missing>}"
      echo ">>> current commit: $current_commit"
      rm -f "$MARKER"
      return 1
    fi

    echo ">>> Success marker already exists; skipping build: $branch"
    echo ">>> marker: $(pwd)/$MARKER"
    return 0
  fi

  return 1
}

mark_built() {
  local branch="$1"

  {
    echo "branch=$branch"
    echo "commit=$(git rev-parse HEAD)"
    echo "built_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  } > "$MARKER"

  echo ">>> Writing success marker: $MARKER"
}

create_worktree() {
  local branch="$1"
  local remote_ref="refs/remotes/$REMOTE/$branch"
  local worktree_dir

  worktree_dir="$(stage_path "$branch")"

  git rev-parse --verify --quiet "$remote_ref" >/dev/null \
    || fail "Remote branch does not exist: $REMOTE/$branch"

  if [[ -d "$worktree_dir" ]]; then
    echo ">>> Worktree directory already exists; reusing without syncing: $worktree_dir"
    if ! git -C "$worktree_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
      echo ">>> Existing worktree metadata is invalid; attempting repair: $worktree_dir"
      run git worktree repair "$worktree_dir"
      git -C "$worktree_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
        || fail "Existing directory is not a valid git worktree after repair: $worktree_dir"
    fi
    return
  fi

  if git show-ref --verify --quiet "refs/heads/$branch"; then
    run git worktree add "$worktree_dir" "$branch"
  else
    run git worktree add -b "$branch" "$worktree_dir" "$REMOTE/$branch"
  fi
}

create_all_worktrees() {
  local branch

  for branch in "${GROUP1[@]}" "${GROUP2[@]}" "${GROUP3_MAKE[@]}" "${GROUP3_ANT[@]}" "${GROUP4[@]}" "${GROUP5[@]}"; do
    create_worktree "$branch"
  done
}

build_group1() {
  local prev=""
  local branch

  for branch in "${GROUP1[@]}"; do
    echo
    echo "========== BUILD GROUP1: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      prev="$branch"
      continue
    fi

    clean_make_tree

    if [[ -z "$prev" ]]; then
      run_build "$branch" make
    elif uses_bootstrap_home; then
      local bootstrap_home
      bootstrap_home="$(absolute_path "$(stage_path "$prev")")"
      run_build "$branch" make BOOTSTRAP_HOME="$bootstrap_home"
    else
      local scalac
      scalac="$(stage_path "$prev")/bin/scalac"
      [[ -x "$scalac" ]] || fail "Previous scalac not found: $scalac"
      run_build "$branch" env BOOTSTRAP_SCALAC="$scalac" make
    fi

    mark_built "$branch"

    popd >/dev/null
    prev="$branch"
  done
}

build_group2() {
  local branch

  for branch in "${GROUP2[@]}"; do
    echo
    echo "========== BUILD GROUP2: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      continue
    fi

    clean_make_tree

    run make

    mark_built "$branch"

    popd >/dev/null
  done
}

build_group3_make() {
  local branch
  local prev="v1.3.0.12-bootstrap"
  local bootstrap_home

  for branch in "${GROUP3_MAKE[@]}"; do
    echo
    echo "========== BUILD GROUP3_MAKE: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      prev="$branch"
      continue
    fi

    [[ -d "$(stage_path "$prev")" ]] || fail "BOOTSTRAP_HOME not found: $(stage_path "$prev")"
    bootstrap_home="$(absolute_path "$(stage_path "$prev")")"

    clean_make_tree
    run make BOOTSTRAP_HOME="$bootstrap_home" all

    mark_built "$branch"

    popd >/dev/null
    prev="$branch"
  done
}

build_group3_ant() {
  local branch

  for branch in "${GROUP3_ANT[@]}"; do
    echo
    echo "========== BUILD GROUP3_ANT: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      continue
    fi

    run env ANT_OPTS="-Xmx512M" ant
    if ant_has_target distrib; then
      run env ANT_OPTS="-Xmx512M" ant distrib
    else
      echo ">>> build.xml has no distrib target; skipping: $branch"
    fi

    mark_built "$branch"

    popd >/dev/null
  done
}

build_group4() {
  local branch
  local prev="way2scala-2.0-stage10-bootstrap"
  local effective_prev
  local starr_lib
  local starr_comp
  local fjbg_jar
  local java_stub_prev
  local java_stub_jar
  local rt_jar
  local ant_props

  for branch in "${GROUP4[@]}"; do
    echo
    echo "========== BUILD GROUP4: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      prev="$branch"
      continue
    fi

    effective_prev="${GROUP4_PREV[$branch]:-$prev}"
    if [[ "$effective_prev" != "$prev" ]]; then
      echo ">>> Using explicit predecessor: $branch <- $effective_prev"
    fi

    starr_lib="$(group4_starr_lib "$effective_prev")"
    starr_comp="$(group4_starr_comp "$effective_prev")"
    fjbg_jar="$(group4_fjbg_jar)"

    ant_props=(
      "-Dstarr.lib.jar=$starr_lib"
      "-Dstarr.comp.jar=$starr_comp"
      "-Dlib.starr.jar=$starr_lib"
      "-Dcomp.starr.jar=$starr_comp"
    )

    bridge_prev="${GROUP4_BRIDGE_PREV[$branch]:-}"
    if [[ -n "$bridge_prev" ]]; then
      echo ">>> Using explicit bridge compiler: $branch <- $bridge_prev"
      ant_props+=(
        "-Dlib.bridge.jar=$(group4_starr_lib "$bridge_prev")"
        "-Dcomp.bridge.jar=$(group4_starr_comp "$bridge_prev")"
      )
    fi

    if group4_uses_source_fjbg "$branch"; then
      echo ">>> Building FJBG from current sources: $branch"
    else
      ant_props+=("-Dfjbg.jar=$fjbg_jar")
    fi

    java_stub_prev="${GROUP4_JAVABOOTCLASSPATH_PREV[$branch]:-}"
    if [[ -n "$java_stub_prev" ]]; then
      [[ -n "${JAVA_HOME:-}" ]] || fail "$branch requires JAVA_HOME to set the Java bootclasspath"
      java_stub_jar="$(absolute_path "$(stage_path "$java_stub_prev")/build/java8-stubs.jar")"
      rt_jar="$(absolute_path "$JAVA_HOME/jre/lib/rt.jar")"
      ant_props+=("-Dscalac.args=-javabootclasspath $java_stub_jar:$rt_jar")
    fi

    run env ANT_OPTS="-Xmx1024M" ant \
      "${ant_props[@]}" \
      $(group4_ant_targets)

    mark_built "$branch"

    popd >/dev/null
    prev="$branch"
  done
}

build_group5() {
  local branch
  local prev="v2.9.3-bootstrap"
  local effective_prev
  local mode

  for branch in "${GROUP5[@]}"; do
    echo
    echo "========== BUILD GROUP5: $branch =========="

    pushd "$(stage_path "$branch")" >/dev/null

    if already_built "$branch"; then
      popd >/dev/null
      prev="$branch"
      continue
    fi

    effective_prev="${GROUP5_PREV[$branch]:-$prev}"
    if [[ "$effective_prev" != "$prev" ]]; then
      echo ">>> Using explicit predecessor: $branch <- $effective_prev"
    fi

    mode="${GROUP5_MODE[$branch]:-all}"
    run "$SCRIPT_DIR/build-scala.sh" "$(pwd)" "$(absolute_path "$(stage_path "$effective_prev")")" "$mode" "$branch"

    mark_built "$branch"

    popd >/dev/null
    prev="$branch"
  done
}

main() {
  parse_args "$@"
  ensure_tools
  mkdir -p "$WORKTREE_ROOT"
  ensure_repo
  create_all_worktrees

  build_group1
  build_group2
  build_group3_make
  build_group3_ant
  build_group4
  build_group5

  echo
  echo "All done."
}

main "$@"

#!/usr/bin/env python3
"""Apply the klimt source patches recorded in PATCHES.json to an Evolution X tree.

usage: apply.py <source tree root> [--check]

For each project in PATCHES.json, in order:

  DIRTY <project>: ...     the project has local changes or untracked files; nothing is done
  HASH <project>: ...      a patch file is missing or its sha256 differs from PATCHES.json
  FINAL <project>          HEAD is already final_head; nothing to do
  BASE <project>: ...      (--check only) HEAD is the recorded base; the patches are not applied yet
  APPLIED <project>        HEAD was the base; the patches were applied on branch `klimt`
                           and HEAD is now final_head
  MISMATCH <project>: ...  HEAD is neither base nor final_head (upstream moved: re-port the
                           patches and run export.py), or applying did not give final_head

Every line on stdout starts with one of these words. Nothing is changed unless every project
passes the DIRTY, HASH and MISMATCH checks first.

Exit codes: 0 = every project is FINAL (or APPLIED); 1 = at least one project is not;
2 = usage error or unreadable PATCHES.json.

--check never changes anything and exits 0 only if every project is FINAL.

The commits are made with `git am --committer-date-is-author-date` and a fixed committer, so the
same patches on the same base always give the same final_head.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

HERE = Path(__file__).resolve().parent
MANIFEST = HERE / 'PATCHES.json'
BRANCH = 'klimt'
COMMITTER_NAME = 'Diogo Casteluber Laass'
COMMITTER_EMAIL = '4793368+iowaz@users.noreply.github.com'
# Settings that would change the commits git am makes; pinned so the result does not depend
# on the user's git configuration.
AM_CONFIG = [
    '-c', 'core.autocrlf=false',
    '-c', 'am.keepcr=true',  # keep CRLF files' line endings (SystemUI's charging files are CRLF)
    '-c', 'am.threeWay=false',
    '-c', 'am.messageid=false',
    '-c', 'apply.whitespace=nowarn',
    '-c', 'commit.gpgSign=false',
]


class GitError(RuntimeError):
    pass


def git(repo, *args, env=None, check=True):
    if env is None:
        # `git status` must not refresh the index of a project that is only being checked.
        env = dict(os.environ, GIT_OPTIONAL_LOCKS='0')
    p = subprocess.run(['git', '-C', str(repo), *args], stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, env=env)
    if check and p.returncode:
        raise GitError('git %s: %s' % (' '.join(args), p.stderr.decode(errors='replace').strip()))
    return p.stdout.decode(errors='replace').strip()


def rev(repo, name):
    p = subprocess.run(['git', '-C', str(repo), 'rev-parse', '-q', '--verify', name],
                       stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return p.stdout.decode().strip() if p.returncode == 0 else None


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_records(path=MANIFEST):
    records = json.loads(Path(path).read_text())
    if not isinstance(records, list):
        raise ValueError('PATCHES.json must be a list')
    return records


def am_env():
    env = dict(os.environ)
    env['GIT_COMMITTER_NAME'] = COMMITTER_NAME
    env['GIT_COMMITTER_EMAIL'] = COMMITTER_EMAIL
    env.pop('GIT_COMMITTER_DATE', None)
    return env


def apply_patches(repo, base, patch_files):
    """Put `repo` on branch klimt at `base` and git am the patches. Returns the new HEAD."""
    git(repo, 'checkout', '-q', '-B', BRANCH, base)
    p = subprocess.run(['git', '-C', str(repo), *AM_CONFIG, 'am', '-q',
                        '--committer-date-is-author-date', *map(str, patch_files)],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=am_env())
    if p.returncode:
        git(repo, 'am', '--abort', check=False)
        raise GitError('git am failed: ' + (p.stderr or p.stdout).decode(errors='replace').strip())
    return rev(repo, 'HEAD')


def inspect(root, item, patch_dir=HERE):
    """Return (word, message, repo) for one project without changing anything."""
    project = item['project']
    repo = Path(root) / project
    if not (repo / '.git').exists():
        return 'MISMATCH', '%s: not a git checkout at %s' % (project, repo), repo
    dirty = git(repo, 'status', '--porcelain', '--untracked-files=all')
    if dirty:
        return 'DIRTY', '%s: %d changed or untracked paths' % (project, len(dirty.splitlines())), repo
    for patch in item['patches']:
        f = patch_dir / patch['file']
        if not f.is_file():
            return 'HASH', '%s: %s is missing' % (project, patch['file']), repo
        actual = sha256(f)
        if actual != patch['sha256']:
            return 'HASH', '%s: %s sha256 %s, PATCHES.json has %s' % (
                project, patch['file'], actual, patch['sha256']), repo
    head = rev(repo, 'HEAD')
    if head == item['final_head']:
        return 'FINAL', project, repo
    if head == item['base'] and rev(repo, 'HEAD^{tree}') == item['base_tree']:
        return 'BASE', '%s: HEAD %s is the base; patches not applied' % (project, head), repo
    note = ''
    if rev(repo, 'HEAD^{tree}') == item['final_tree']:
        note = ' (its tree is final_tree: an equivalent commit, such as an old fork head)'
    return 'MISMATCH', '%s: HEAD %s is neither base %s nor final_head %s%s' % (
        project, head, item['base'], item['final_head'], note), repo


def apply_one(repo, item, patch_dir=HERE):
    head = apply_patches(repo, item['base'], [patch_dir / p['file'] for p in item['patches']])
    tree = rev(repo, 'HEAD^{tree}')
    if head != item['final_head'] or tree != item['final_tree']:
        return 'MISMATCH', '%s: applied HEAD %s tree %s, expected final_head %s tree %s' % (
            item['project'], head, tree, item['final_head'], item['final_tree'])
    return 'APPLIED', item['project']


def main(argv):
    args = [a for a in argv if a != '--check']
    check = len(args) != len(argv)
    if len(args) != 1 or args[0].startswith('-'):
        print('usage: apply.py <source tree root> [--check]', file=sys.stderr)
        return 2
    root = Path(args[0]).resolve()
    try:
        records = load_records()
    except (OSError, ValueError) as e:
        print('cannot read %s: %s' % (MANIFEST, e), file=sys.stderr)
        return 2

    states = []
    for item in records:
        try:
            states.append((item,) + inspect(root, item))
        except GitError as e:
            states.append((item, 'MISMATCH', '%s: %s' % (item['project'], e), None))

    ok_words = {'FINAL'} if check else {'FINAL', 'BASE'}
    blocked = any(word not in ok_words for _, word, _, _ in states)
    failed = False
    for item, word, msg, repo in states:
        if word == 'BASE' and not check and not blocked:
            try:
                word, msg = apply_one(repo, item)
            except GitError as e:
                word, msg = 'MISMATCH', '%s: %s' % (item['project'], e)
        if word not in ('FINAL', 'APPLIED'):
            failed = True
        print(word + ' ' + msg, flush=True)
    if blocked and not check:
        print('apply.py: nothing applied; fix the projects above first', file=sys.stderr)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))

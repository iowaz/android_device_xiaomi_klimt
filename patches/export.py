#!/usr/bin/env python3
"""Regenerate patches and PATCHES.json records from a source tree.

usage: export.py <source tree root> [PROJECT ...] [--base PROJECT=SHA ...] [--manifest FILE]
                 [--scratch DIR] [--keep-scratch]

PROJECT is a path from PATCHES.json (default: every project in it). To add a new project, first
add a stub record {"project": ..., "class": "klimt"|"personal", "purpose": ...} to PATCHES.json.

In the source tree, each exported project must have its commits checked out on top of an upstream
base: HEAD = base + the commits to export, no merges. The base of each project is, in this order:

  1. --base PROJECT=SHA, if given;
  2. the `revision` of that project's path in --manifest FILE (a frozen repo manifest, which pins
     plain upstream SHAs once the forks are gone);
  3. the base already in PATCHES.json, if it is still an ancestor of HEAD (re-export after
     amending a patch on the same base).

For each project export.py writes `git format-patch <base>..HEAD` into a staging folder, applies
the result twice with apply.py's own code in a scratch repository that borrows the project's
objects (alternates, like `git clone --shared`; never in the source
tree), requires both runs to give the same commit and the tree of the source HEAD, and records
base, base_tree, final_tree, final_head and the patch hashes. Only when every requested project
passes are the patch folders replaced and PATCHES.json rewritten; otherwise nothing is written.

The source tree is only read. The scratch clones go to a temporary folder (or --scratch DIR) and
are deleted unless --keep-scratch is given.

Exit codes: 0 = exported; 1 = a project failed (nothing written); 2 = usage error.
"""
from pathlib import Path
import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True  # keep __pycache__ out of the device tree
sys.path.insert(0, str(Path(__file__).resolve().parent))
import apply as A  # noqa: E402

FORMAT_PATCH = [
    '-c', 'core.autocrlf=false', '-c', 'core.quotePath=true',
    '-c', 'diff.noprefix=false', '-c', 'diff.mnemonicPrefix=false',
    '-c', 'diff.renames=true', '-c', 'diff.algorithm=myers',
    '-c', 'format.signOff=false',
    'format-patch', '-q', '--zero-commit', '--no-signature', '--full-index', '--no-numbered',
    '--no-to', '--no-cc', '--no-thread', '--no-cover-letter', '--filename-max-length=64',
]
SHA = re.compile(r'^[0-9a-f]{40}$')


def scratch_clone(src, dest):
    """A clone that borrows src's objects, like `git clone --shared`.

    repo syncs most projects shallow, and `git clone --shared` of a shallow repository falls back
    to copying every object. Instead point a new repository's alternates at src's object store
    and copy its shallow file. Nothing in src is written.
    """
    objects = A.git(src, 'rev-parse', '--path-format=absolute', '--git-path', 'objects')
    shallow = Path(A.git(src, 'rev-parse', '--path-format=absolute', '--git-path', 'shallow'))
    subprocess.run(['git', 'init', '-q', str(dest)], check=True)
    gitdir = Path(dest) / '.git'
    (gitdir / 'objects' / 'info' / 'alternates').write_text(objects + '\n')
    if shallow.is_file():
        shutil.copyfile(shallow, gitdir / 'shallow')


def manifest_revisions(path):
    revs = {}
    for p in ET.parse(path).getroot().iter('project'):
        revs[p.get('path') or p.get('name')] = p.get('revision')
    return revs


def resolve_base(repo, item, overrides, pins):
    project = item['project']
    if project in overrides:
        base, src = overrides[project], '--base'
    elif project in pins:
        base, src = pins[project], '--manifest'
    elif item.get('base'):
        base, src = item['base'], 'PATCHES.json'
    else:
        raise RuntimeError('no base: give --base %s=SHA or --manifest' % project)
    full = A.rev(repo, base + '^{commit}')
    if not full:
        raise RuntimeError('base %s (from %s) is not a commit here' % (base, src))
    if not SHA.match(base) and src == '--manifest':
        raise RuntimeError('manifest revision %r is not a commit SHA' % base)
    if subprocess.run(['git', '-C', str(repo), 'merge-base', '--is-ancestor', full, 'HEAD']).returncode:
        raise RuntimeError('base %s (from %s) is not an ancestor of HEAD' % (full, src))
    return full


def export_one(root, item, base, stage, scratch, old_classes):
    project = item['project']
    repo = Path(root) / project
    head = A.rev(repo, 'HEAD')
    if A.git(repo, 'rev-list', '--merges', base + '..HEAD'):
        raise RuntimeError('merge commits between base and HEAD')
    subjects = A.git(repo, 'log', '--reverse', '--format=%s', base + '..HEAD').splitlines()
    if not subjects:
        raise RuntimeError('no commits between base %s and HEAD' % base)

    out = stage / project
    out.mkdir(parents=True)
    A.git(repo, *FORMAT_PATCH, '-o', str(out), base + '..HEAD')
    files = sorted(out.glob('*.patch'))
    if len(files) != len(subjects):
        raise RuntimeError('format-patch wrote %d files for %d commits' % (len(files), len(subjects)))

    clone = scratch / project.replace('/', '_')
    scratch_clone(repo, clone)
    finals = []
    for _ in range(2):
        A.git(clone, 'checkout', '-q', '--detach', '-f', base)
        A.git(clone, 'clean', '-q', '-fdx')
        finals.append(A.apply_patches(clone, base, files))
    if finals[0] != finals[1]:
        raise RuntimeError('two applies gave different commits: %s %s' % tuple(finals))
    final_tree = A.rev(clone, 'HEAD^{tree}')
    head_tree = A.rev(repo, 'HEAD^{tree}')
    if final_tree != head_tree:
        raise RuntimeError('applied tree %s differs from source HEAD tree %s' % (final_tree, head_tree))

    patches = []
    for f, subject in zip(files, subjects):
        cls = old_classes.get(subject)
        if cls is None:
            cls = item['class']
            print('note: %s: new patch %r gets the project class %s' % (project, subject, cls),
                  file=sys.stderr)
        patches.append({'file': '%s/%s' % (project, f.name), 'sha256': A.sha256(f),
                        'class': cls, 'subject': subject})
    record = {'project': project, 'class': item['class'], 'purpose': item['purpose'],
              'base': base, 'base_tree': A.rev(repo, base + '^{tree}'), 'final_tree': final_tree,
              'final_head': finals[0], 'patches': patches}
    record.update((k, v) for k, v in item.items() if k not in record)
    return record, head


def main(argv):
    ap = argparse.ArgumentParser(description='Regenerate patches and PATCHES.json from a source tree.')
    ap.add_argument('root')
    ap.add_argument('projects', nargs='*')
    ap.add_argument('--base', action='append', default=[], metavar='PROJECT=SHA')
    ap.add_argument('--manifest')
    ap.add_argument('--scratch')
    ap.add_argument('--keep-scratch', action='store_true')
    a = ap.parse_args(argv)

    records = A.load_records()
    by_project = {r['project']: r for r in records}
    wanted = a.projects or [r['project'] for r in records]
    unknown = [p for p in wanted if p not in by_project]
    overrides = dict(b.split('=', 1) for b in a.base)
    unknown += [p for p in overrides if p not in wanted]
    if unknown:
        print('not in PATCHES.json or not selected: ' + ', '.join(unknown), file=sys.stderr)
        return 2
    pins = manifest_revisions(a.manifest) if a.manifest else {}

    if a.scratch:
        Path(a.scratch).mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='klimt-export-', dir=a.scratch))
    stage, scratch = work / 'stage', work / 'clones'
    scratch.mkdir(parents=True)
    results, failed = {}, False
    try:
        for project in wanted:
            item = by_project[project]
            repo = Path(a.root).resolve() / project
            old_classes = {p.get('subject'): p.get('class') for p in item.get('patches', [])
                           if p.get('subject')}
            try:
                base = resolve_base(repo, item, overrides, pins)
                record, head = export_one(Path(a.root).resolve(), item, base, stage, scratch, old_classes)
            except (RuntimeError, A.GitError, subprocess.CalledProcessError) as e:
                print('FAILED %s: %s' % (project, e))
                failed = True
                continue
            results[project] = record
            print('EXPORTED %s: %d patches, base %s, final_head %s (source HEAD %s)' % (
                project, len(record['patches']), record['base'], record['final_head'], head))
        if failed:
            print('export.py: nothing written', file=sys.stderr)
            return 1
        here = A.HERE
        for project, record in results.items():
            dest = here / project
            if dest.is_dir():
                for old in dest.glob('*.patch'):
                    old.unlink()
            dest.mkdir(parents=True, exist_ok=True)
            for f in sorted((stage / project).glob('*.patch')):
                shutil.copyfile(f, dest / f.name)
            by_project[project] = record
        records = [by_project[r['project']] for r in records]
        A.MANIFEST.write_text(json.dumps(records, indent=2) + '\n')
        return 0
    finally:
        if a.keep_scratch:
            print('scratch kept at %s' % work, file=sys.stderr)
        else:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))

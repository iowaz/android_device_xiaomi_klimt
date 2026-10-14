#!/usr/bin/env python3
"""Apply exact reviewed bases; skip only complete final-tree identity."""
from pathlib import Path
import hashlib,json,subprocess,sys
root=Path(sys.argv[1]).resolve();here=Path(__file__).resolve().parent
records=json.loads((here/'PATCHES.json').read_text())
def git(repo,*args):
 p=subprocess.run(['git','-C',str(repo),*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
 return p.stdout.decode().strip()
for item in records:
 repo=root/item['project']
 if git(repo,'status','--porcelain','--untracked-files=all'):raise RuntimeError('Dirty project: '+item['project'])
 for patch in item['patches']:
  if hashlib.sha256((here/patch['file']).read_bytes()).hexdigest()!=patch['sha256']:raise RuntimeError('Patch hash mismatch: '+patch['file'])
 if git(repo,'rev-parse','HEAD^{tree}')==item['final_tree']:
  print('Equivalent full final tree; skip '+item['project']);continue
 if git(repo,'rev-parse','HEAD')!=item['base'] or git(repo,'rev-parse','HEAD^{tree}')!=item['base_tree']:
  raise RuntimeError('Ambiguous/incompatible base: '+item['project'])
 for patch in item['patches']:
  git(repo,'am',str(here/patch['file']))
 if git(repo,'rev-parse','HEAD^{tree}')!=item['final_tree']:raise RuntimeError('Final source tree mismatch: '+item['project'])
 print('Reviewed source tree applied: '+item['project'])

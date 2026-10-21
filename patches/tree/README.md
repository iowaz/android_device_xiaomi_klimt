# Exact-base patch set

Four projects carry a local change on top of Evolution X 12.3 (`cnb`):

- `build/soong`: the MIUI boot jar packages (`miui`, `miui.*`) are allowed on the boot class path
  (since evox15), and `gen_build_prop.py` no longer writes `ro.lineage.device` and
  `ro.evolution.device` (evox20);
- `frameworks/native`: libui asks the MediaTek mapper once whether it lists SMPTE 2094-50 metadata
  instead of failing (and logging) on every buffer (evox13);
- `device/lineage/sepolicy`: the adb root policy goes only into userdebug and eng builds, the ones
  that ship `adb_root`; a user build no longer allows adbd to call an adbroot domain (evox21);
- `frameworks/base`: the in-framework TrickyStore leaf-hack persists the rewritten certificate
  chain into keystore2 at key generation (for LEAF_HACK targets only), so the owner, grant and
  raw-binder read paths all return the same chain (evox22). The already-present Evox24
  default PIF-config seeding change is also exported so this bundle reproduces the current final tree.

The build/soong and frameworks/native commits are on the iowaz forks' `evox16` branches, which the
local manifest uses; the device/lineage/sepolicy commit is not published yet. `apply.sh`
is for a plain Evolution X checkout: it checks the recorded base and patch hashes, applies the patches
and verifies the final tree; it skips a project whose tree is already final and refuses dirty projects.

evox16 exported twelve more projects here. They were reverted to upstream for evox17 because none of
them was needed for klimt; see `docs/evox17.md` in the build repository. `../tree-evox15` keeps the
evox15 picks as history; all of them are in the 12.3 sources.

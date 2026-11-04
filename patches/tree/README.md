# Exact-base patch set

Five projects carry a local change on top of Evolution X 12.3 (`cnb`) that is exported here:

- `build/make`: vendor build.prop honors the same ID and incremental overrides as Soong (evox26);
- `build/soong`: the MIUI boot jar packages (`miui`, `miui.*`) are allowed on the boot class path
  (since evox15), and `gen_build_prop.py` no longer writes `ro.lineage.device` and
  `ro.evolution.device` (evox20);
- `frameworks/native`: libui asks the MediaTek mapper once whether it lists SMPTE 2094-50 metadata
  instead of failing (and logging) on every buffer (evox13). The evox27/evox28 GED KPI fixes were
  dropped in evox31: Evolution X's rewritten GED KPI hooks (74d98cda) initialize the producer pid and
  surfaceflinger flag, declare them for every module and probe /proc/ged once per process;
- `device/lineage/sepolicy`: the adb root policy goes only into userdebug and eng builds, the ones
  that ship `adb_root`; a user build no longer allows adbd to call an adbroot domain (evox21);
- `frameworks/base`: the package manager's upgrade digest (`PackagePartitions.FINGERPRINT`) also
  includes `ro.build.date.utc`, because klimt reports a fixed stock build identity; each new build
  then counts as an upgrade and gets a fresh package parser cache (evox27). The evox27 all-zero
  verified boot key fix was dropped in evox31: Evolution X now replaces a zeroed boot key and hash
  itself (3bedba09, 435c37cb). The evox22 chain-persist and evox24 PIF-seeding changes were dropped in evox27:
  upstream's TrickyStore rework persists the hacked chain at key generation itself.

The evox31 series is recorded below with exact upstream bases and final trees;
the frozen manifest records the corresponding fork commits. `apply.sh`
is for a plain Evolution X checkout: it checks the recorded base and patch hashes, applies the patches
and verifies the final tree; it skips a project whose tree is already final and refuses dirty projects.

evox16 exported twelve more projects here. They were reverted to upstream for evox17 because none of
them was needed for klimt; see `docs/evox17.md` in the build repository. `../tree-evox15` keeps the
evox15 picks as history; all of them are in the 12.3 sources.

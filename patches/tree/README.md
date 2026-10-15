# Evox17 exact-base patch set

Two projects carry a local change on top of Evolution X 12.3 (`cnb`). Both were already in
evox15, which runs on the phone:

- `build/soong`: the MIUI boot jar packages (`miui`, `miui.*`) are allowed on the boot class path;
- `frameworks/native`: libui asks the MediaTek mapper once whether it lists SMPTE 2094-50 metadata
  instead of failing (and logging) on every buffer (evox13).

The same commits are on the iowaz forks' `evox16` branches, which the local manifest uses. `apply.sh`
is for a plain Evolution X checkout: it checks the recorded base and patch hashes, applies the patches
and verifies the final tree; it skips a project whose tree is already final and refuses dirty projects.

evox16 exported twelve more projects here. They were reverted to upstream for evox17 because none of
them was needed for klimt; see `docs/evox17.md` in the build repository. `../tree-evox15` keeps the
evox15 picks as history; all of them are in the 12.3 sources.

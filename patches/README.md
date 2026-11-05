# Source patches

klimt builds on plain Evolution X (`cnb`). The few changes it needs outside this device tree are kept
here as patches instead of in forks of the ROM projects. `PATCHES.json` records, for each project,
the upstream commit the patches apply on, their sha256 and the exact commit and tree that applying
them gives.

`MiuiCamera/` is unrelated: those patches are applied to the MIUI Camera blobs by
`extract-files.py`.

## Rule

Only klimt-specific patches, and only when the owner asks for them. The personal identity layer
(class `personal` below) is kept because the owner explicitly asked for it. Generic changes that
are not about klimt are not carried here: the init spoof-marker changes (`system/core`) were dropped
when the forks were retired. The adb root sepolicy gate (`device/lineage/sepolicy`) was dropped with
them and is back since 2026-11-05 at the owner's request, as a `personal` patch (below).

## The patches

Class `klimt`: the device needs them.

| Project | Patch | Why |
|---|---|---|
| hardware/interfaces | Drop the light@2.0 HIDL interface | The stock vendor ships `android.hardware.light@2.0.so` as a vendor prebuilt that Soong cannot swap for the source module; the build fails (evox1) |
| hardware/interfaces | blank_screen: Use only the AIDL lights HAL | Evolution X's HIDL light@2.0 fallback needs the interface dropped above; the build fails (evox3) |
| frameworks/native | libui: Preserve the supported SMPTE2094_50 metadata guard | The MediaTek mapper does not list SMPTE 2094-50; libui asks once instead of failing and logging on every buffer (evox13) |
| build/soong | Retain MIUI boot jar package allowance | The MIUI jars on the boot class path (`miui`, `miui.*`, since evox15). Can go once klimt uses LineageOS `hardware/xiaomi`'s shared eSIM and drops `miui-stub/` |

Class `personal`: the owner's identity layer (evox20, evox26, evox27). The Evolution X build info
moves from `ro.evolution.*` / `*.device` props to build constants, and the vendor props carry the
Xiaomi identity. The `vendor/lineage` patch generates the constants (`evolution-build-info`) that
the lineage-sdk, Settings, Updater, recovery and build/soong patches read, so these go together.

| Project | Patch | What it does |
|---|---|---|
| build/soong | gen_build_prop: Stop writing ro.lineage.device and ro.evolution.device | The two device props are no longer written |
| build/make | sysprop: Honor configured vendor build identity overrides | vendor build.prop uses the same build ID and incremental overrides as Soong |
| vendor/lineage | evolution: Pass build info to Soong instead of ro.evolution props | Build type, device and versions become compile-time constants |
| lineage-sdk | sdk: Take the Evolution X versions from build constants (+ comment fix) | `Build.EVOLUTION_VERSION` and `EVOLUTION_DISPLAY_VERSION` from the constants |
| packages/apps/Settings | ModelPageController: Take the build type and device from build constants | Same values on the model page |
| packages/apps/Updater | Updater: Take the device name from a build constant | Same OTA and changelog URLs |
| bootable/recovery | recovery: Take the Evolution X version from a build constant | Same menu title |
| device/lineage/sepolicy | sepolicy: Keep the adb root policy and domain to debuggable builds | A user build ships no `adb_root` (vendor/lineage installs it only with `WITH_SU`), yet its policy declared the `adbroot` domain and allowed `adbd -> adbroot` binder calls. Duck Detector's SELinux check reads both from an app (20261105 scan). The domain and its rules go into userdebug and eng builds only; the exec, data and service types stay for the contexts files. Not part of the identity layer and independent of the other patches |
| frameworks/base | PackagePartitions: Include the build time in the upgrade fingerprint | klimt reports a fixed stock Xiaomi fingerprint, so without `ro.build.date.utc` in the digest a new build does not count as an upgrade and keeps a stale package parser cache. Keep it while the device keeps the fixed fingerprint |

## Layout

    patches/<project path>/NNNN-<subject>.patch   git format-patch output, one folder per project
    patches/PATCHES.json                          one record per project, in apply order
    patches/apply.py, apply.sh                    apply or check the patches in a source tree
    patches/export.py                             regenerate patches and records from a source tree

A `PATCHES.json` record:

    project      project path in the source tree
    class        klimt or personal (each patch also has its own class and subject)
    purpose      one sentence
    base         upstream commit the patches apply on (the frozen manifest pins this SHA)
    base_tree    tree of base
    final_head   commit that apply.py produces
    final_tree   tree of final_head
    patches      [{file (relative to patches/), sha256, class, subject}]

## Applying

After `repo sync`:

    python3 device/xiaomi/klimt/patches/apply.py <source tree root>          # or apply.sh
    python3 device/xiaomi/klimt/patches/apply.py <source tree root> --check

For each project, in order, it prints one line that starts with:

| Word | Meaning |
|---|---|
| `FINAL <project>` | HEAD is `final_head`; nothing to do |
| `APPLIED <project>` | HEAD was `base`; now on local branch `klimt` at `final_head` |
| `BASE <project>: ...` | `--check` only: HEAD is `base`, the patches are not applied yet |
| `MISMATCH <project>: ...` | HEAD is neither `base` nor `final_head` (upstream moved: port the patches, then run `export.py`), or applying did not give `final_head` |
| `DIRTY <project>: ...` | the project has changes or untracked files |
| `HASH <project>: ...` | a patch file is missing or does not match its sha256 |

Nothing is applied unless every project passes the DIRTY, HASH and MISMATCH checks. For a project
at `base` it runs `git checkout -B klimt <base>` and `git am --committer-date-is-author-date` with
a fixed committer (Diogo Casteluber Laass), so the result is the same commit on every machine, and
then checks it against `final_head` and `final_tree`. A second run prints `FINAL` for every project.

A tree still on the evox31 fork commits prints `MISMATCH ... (its tree is final_tree ...)`: the
content is the same, but the commits differ (their committer dates), so sync to the upstream pins
first.

Exit codes: 0 when every project is `FINAL` or `APPLIED`; 1 when any is not; 2 for a usage error or
an unreadable `PATCHES.json`. `--check` changes nothing and exits 0 only if every project is `FINAL`.

## Updating after upstream moves

When `apply.py` prints `MISMATCH` because Evolution X moved a project:

1. In that project, check out the new upstream commit and cherry-pick or port the patches onto it
   (`git am -3 <source tree root>/device/xiaomi/klimt/patches/<project>/*.patch`, fix conflicts).
   Build and test.
2. Regenerate the patches and the record:

       python3 device/xiaomi/klimt/patches/export.py <source tree root> <project> --base <project>=<new upstream SHA>

   or, with a frozen manifest that pins the new upstream SHAs, for every project at once:

       python3 device/xiaomi/klimt/patches/export.py <source tree root> --manifest <frozen manifest.xml>

   Without `--base` or `--manifest` the base already in `PATCHES.json` is used, if it is still an
   ancestor of HEAD (for amending a patch on the same base).
3. Commit the changed `patches/<project>/` and `PATCHES.json` in the device tree.

`export.py` only reads the source tree. It runs `git format-patch <base>..HEAD` into a staging
folder, applies the result twice with `apply.py`'s code in a scratch repository that borrows the
project's objects (alternates, as `git clone --shared` does; repo's shallow projects would make a
real `git clone --shared` copy every object), requires
both runs to give the same commit and the tree of the source HEAD, and records the hashes,
`base_tree`, `final_tree` and `final_head`. If any requested project fails it writes nothing. Use
`--scratch DIR` to put the scratch clones somewhere other than the temp folder, `--keep-scratch`
to keep them. To add a project, put a stub record (`project`, `class`, `purpose`) into
`PATCHES.json` first.

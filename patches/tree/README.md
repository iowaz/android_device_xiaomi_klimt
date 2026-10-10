# Source patches for the klimt Evolution X build (evox9 / evox10)

Local commits in projects that have no klimt fork, exported with `git format-patch`. They sit on the
Evolution X `cnb` revisions in `BASES` (the 2026-10-09 snapshot); upstream has since rebased most of these
repositories, so they are kept here instead of in forks. Apply with `patches/tree/apply.sh <tree root>`.

- bionic: custom_rom_hide synced to upstream 3d41693 (plus its header and sys_statfs.cpp parts).
- frameworks/base: PIF default profile (Pixel 6a canary) from upstream, PduParser October 2026 security fixes
  (LineageOS 47a31e21f0, f4ed08a03b; tests left out), ROM packages hidden from unprivileged apps (upstream b0a4ac0d11).
- frameworks/av: hide the non-AOSP ANetworkSession symbols (upstream 7eaa22020c).
- frameworks/opt/telephony: decodeWapPdu remote DoS fix (upstream 71d68ae390).
- packages/apps/Evolver: PlayIntegrityFix follows the frameworks PIF default (upstream 5a5cfae).
- packages/modules/Bluetooth: CVE-2026-58880 fix (upstream 71940ed630).
- system/core: stop spoofing sys.oem_unlock_allowed (upstream ee96af1583).

Other local changes live in their own repositories: hardware_interfaces and build_soong (iowaz forks, branch
cnb-klimt), vendor_evolution (private iowaz repo, cnb-klimt) and the proprietary vendor tree (private).

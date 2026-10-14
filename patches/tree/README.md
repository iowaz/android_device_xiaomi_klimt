# Evox16 exact-base patch set

BASES and PATCHES.json record exact upstream bases, patch SHA256 values and reviewed final source trees. apply.sh rejects dirty projects and incompatible bases, applies every patch, and verifies the complete resulting tree. It skips only a complete final-tree identity; it never relies on commit subjects or a short log window. A failed application preserves Git am state for diagnosis.

Previous eight-project exports are retained in ../tree-evox15 for rollback evidence, not automatically reapplied. Bionic, AV symbol hiding, framework PIF/PduParser/ROM hiding, WAP, Evolver PIF, AVRCP and init-property picks were positively superseded by the audited targets. Required native metadata behavior is re-exported here. Existing lights commits remain in the pinned iowaz hardware/interfaces fork.

These derived commits are local and unpublished. The resolved local build manifest records their actual SHAs. A portable recipe must fetch the recorded bases and apply this patch set; it must not claim unpublished commits are available remotely. Device-only TouchBridge/allocator changes are in device history; sensor parser is exported here. Kernel and proprietary blob identities remain fixed.

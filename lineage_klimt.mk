#
# SPDX-FileCopyrightText: WitAqua
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Evolution X
WITH_GMS := true
# Evolution X's Pixel fingerprint override, needed for Play Integrity with its keybox.
# libinit still sets the klimt attestation IDs.
TARGET_ENABLE_FP_OVERRIDE := true
# No Columbus (back tap) sensor on klimt
TARGET_SUPPORTS_QUICK_TAP := false
# Boot animation size
TARGET_SCREEN_WIDTH := 1280
TARGET_SCREEN_HEIGHT := 2772

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from klimt device
$(call inherit-product, device/xiaomi/klimt/device.mk)

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# Keep Klimt's black AOD default above common product overlays.
PRODUCT_PACKAGE_OVERLAYS := \
    device/xiaomi/klimt/overlay-product \
    $(PRODUCT_PACKAGE_OVERLAYS)

PRODUCT_DEVICE := klimt
PRODUCT_NAME := lineage_klimt
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := 2506BPN68G
PRODUCT_MANUFACTURER := Xiaomi

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    DeviceProduct=klimt_global

# evox20: the build identity matches Evolution X's bluejay canary fingerprint
# override (vendor/lineage/config/evolution.mk). The product identity
# (ro.product.*) stays klimt's: the vendor KeyMint, Google ID and TEE code read it.
# gen_build_prop.py rejects unknown keys; Make's vendor/build.prop ignores these.
ifeq ($(TARGET_ENABLE_FP_OVERRIDE),true)
PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildId=ZP11.260918.007 \
    BuildNumber=16484274 \
    BuildFlavor=bluejay_beta-user \
    BuildDesc="bluejay_beta-user CANARY ZP11.260918.007 16484274 release-keys" \
    BuildSystemDesc="bluejay_beta-user CANARY ZP11.260918.007 16484274 release-keys" \
    BuildSystemFingerprint=google/bluejay_beta/bluejay:CANARY/ZP11.260918.007/16484274:user/release-keys \
    Platform_security_patch=2026-10-05 \
    BuildHostname=r-06b84ad74750c689-xc7u
endif

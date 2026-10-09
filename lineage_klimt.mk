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

PRODUCT_DEVICE := klimt
PRODUCT_NAME := lineage_klimt
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := 2506BPN68G
PRODUCT_MANUFACTURER := Xiaomi

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="klimt_global-user 15 AP3A.240905.015.A2 OS3.0.335.0.XOSMIXM release-keys" \
    DeviceProduct=klimt_global

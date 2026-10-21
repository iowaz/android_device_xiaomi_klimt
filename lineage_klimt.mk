#
# SPDX-FileCopyrightText: WitAqua
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Evolution X
WITH_GMS := true
# Use the owner's klimt_global reference instead of the upstream Pixel default.
TARGET_ENABLE_FP_OVERRIDE := false
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

# evox26: tools/evox25-prop-fix.sh reference, applied at image generation.
KLIMT_BUILD_FINGERPRINT := Xiaomi/klimt_global/klimt:17/OS3.3.260806.171839609/OS3.0.335.0.XOSMIXM:user/release-keys
# Also cover Make-generated AVB fingerprint metadata, not only Soong build.prop.
BUILD_FINGERPRINT := $(KLIMT_BUILD_FINGERPRINT)
BUILD_SYSTEM_FINGERPRINT := $(KLIMT_BUILD_FINGERPRINT)

PRODUCT_BUILD_PROP_OVERRIDES += \
    DeviceProduct=klimt_global \
    BuildId=OS3.3.260806.171839609 \
    BuildNumber=OS3.0.335.0.XOSMIXM \
    BuildFlavor=klimt_global-user \
    BuildDesc="klimt_global-user 17 OS3.3.260806.171839609 OS3.0.335.0.XOSMIXM release-keys" \
    BuildSystemDesc="klimt_global-user 17 OS3.3.260806.171839609 OS3.0.335.0.XOSMIXM release-keys" \
    BuildFingerprint=$(KLIMT_BUILD_FINGERPRINT) \
    BuildSystemFingerprint=$(KLIMT_BUILD_FINGERPRINT) \
    Platform_security_patch=2026-10-05 \
    BuildHostname=pangu-build-component-system-247-r6kfm \
    BuildUsername=builder \
    Platform_version_known_codenames=Base,Base11,Cupcake,Donut,Eclair,Eclair01,EclairMr1,Froyo,Gingerbread,GingerbreadMr1,Honeycomb,HoneycombMr2,IceCreamSandwich,IceCreamSandwichMr1,JellyBean,JellyBeanMr1,JellyBeanMr2,Kitkat,KitkatWatch,Lollipop,LollipopMr1,M,N,NMr1,O,OMr1,P,Q,R,S,Sv2,Tiramisu,UpsideDownCake,VanillaIceCream,Baklava,CinnamonBun

# Override the optional generated display ID on both historical emitters.
PRODUCT_SYSTEM_PROPERTIES += ro.build.display.id=OS3.3.260806.171839609
PRODUCT_PRODUCT_PROPERTIES += ro.build.display.id=OS3.3.260806.171839609

# Runtime compatibility alias used by the reference profile.
PRODUCT_SYSTEM_PROPERTIES += ro.bootimage.fingerprint=$(KLIMT_BUILD_FINGERPRINT)

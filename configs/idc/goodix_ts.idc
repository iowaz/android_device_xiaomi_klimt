#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# goodix_ts: the klimt touch panel (Goodix THP, vendor.xiaomi.hw.touchfeature).
#
# With doze.display.supported=true AOD puts the display in DOZE. InputReader
# disables a touchscreen whose display viewport is not active, but in the
# panel's doze mode the THP keeps reporting ordinary touches and raises neither
# its FOD press (fod_press_status, the UDFPS wake-up sensor) nor double tap
# (KEY_WAKEUP). HyperOS keeps the touchscreen live during AOD and handles these
# touches in the framework. Do the same, so SystemUI receives AOD touches: the
# UDFPS overlay for fingerprint and the doze tap / double-tap gestures.
touch.enableForInactiveViewport = 1

/*
 * Copyright (C) 2022 The LineageOS Project
 * SPDX-FileCopyrightText: WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.klimt"

#include <aidl/android/hardware/biometrics/fingerprint/AcquiredInfo.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include <cstddef>
#include <cstdint>

#include "UdfpsHandler.h"

using aidl::android::hardware::biometrics::fingerprint::AcquiredInfo;

namespace {

constexpr char kTouchPath[] = "/dev/xiaomi-touch";
constexpr char kDisplayPath[] = "/dev/mi_display/disp_feature";
constexpr uint16_t kFodEnable = 10;
constexpr int kPressCommand = 1;
constexpr int kNitCommand = 10;

// Dali touch ABI used by the stock touchfeature service. This is not the
// int[256] ioctl ABI used by older Xiaomi MediaTek devices.
struct TouchRequest {
    uint8_t touchId;
    uint8_t command;
    uint16_t mode;
    uint16_t count;
    uint16_t reserved;
    int32_t values[256];
};
static_assert(sizeof(TouchRequest) == 0x408);
static_assert(offsetof(TouchRequest, values) == 8);
constexpr unsigned long kSelectTouch = _IOC(_IOC_WRITE, 'T', 3, 0);
constexpr unsigned long kTouchMode = _IOWR('T', 0, TouchRequest);
static_assert(kSelectTouch == 0x40005403);
static_assert(kTouchMode == 0xc4085400);

// MI_DISP_IOCTL_SET_LOCAL_HBM, as used by the stock mfp-daemon. Unlike the
// disp_param sysfs node this goes through the display's LHBM thread, which
// synchronises the panel command with the frame.
struct LocalHbmRequest {
    uint32_t flag;
    uint32_t displayId;
    uint32_t value;
};
constexpr unsigned long kSetLocalHbm = _IOW('D', 0x0e, LocalHbmRequest);
static_assert(kSetLocalHbm == 0x400c440e);
constexpr uint32_t kLocalHbmOffFingerUp = 0;
constexpr uint32_t kLocalHbmOffAuthStop = 1;
constexpr uint32_t kLocalHbmOnWhite1000Nit = 2;

class KlimtUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* device) override {
        mDevice = device;
        mDisplayFd.reset(open(kDisplayPath, O_RDWR | O_CLOEXEC));
        if (mDisplayFd < 0) {
            PLOG(ERROR) << "Cannot open " << kDisplayPath;
        }
        mTouchFd.reset(open(kTouchPath, O_RDWR | O_CLOEXEC));
        if (mTouchFd < 0) {
            PLOG(ERROR) << "Cannot open " << kTouchPath;
        } else if (ioctl(mTouchFd.get(), kSelectTouch, 0) < 0) {
            PLOG(ERROR) << "Cannot select the primary touch panel";
            mTouchFd.reset();
        }
    }

    void onFingerDown(uint32_t, uint32_t, float, float) override {
        setFodEnabled(true);
        // The nit command tells the module the spot is lit and it may capture,
        // so the local HBM request has to go out first.
        setLocalHbm(kLocalHbmOnWhite1000Nit);
        sendCommand(kPressCommand, 1);
        sendCommand(kNitCommand, 1);
    }

    void onFingerUp() override { releaseFinger(); }

    void onAcquired(int32_t result, int32_t vendorCode) override {
        if (static_cast<AcquiredInfo>(result) == AcquiredInfo::GOOD) {
            releaseFinger();
        } else if (static_cast<AcquiredInfo>(result) == AcquiredInfo::VENDOR &&
                   (vendorCode == 21 || vendorCode == 23)) {
            // The module is ready for authentication or enrollment.
            setFodEnabled(true);
        }
    }

    void onAuthenticationSucceeded() override { cancel(); }

    void cancel() override {
        releaseFinger();
        setLocalHbm(kLocalHbmOffAuthStop);
        setFodEnabled(false);
    }

  private:
    void sendCommand(int command, int value) {
        if (mDevice == nullptr || mDevice->extCmd == nullptr) {
            LOG(ERROR) << "Fingerprint extension command is unavailable";
            return;
        }
        const int result = mDevice->extCmd(mDevice, command, value);
        if (result < 0) {
            LOG(ERROR) << "Fingerprint command " << command << " failed: " << result;
        }
    }

    void setFodEnabled(bool enabled) {
        if (mTouchFd < 0) return;
        TouchRequest request{};
        request.mode = kFodEnable;
        request.count = 1;
        request.values[0] = enabled;
        if (ioctl(mTouchFd.get(), kTouchMode, &request) < 0) {
            PLOG(ERROR) << "Cannot change fingerprint touch mode";
        }
    }

    void setLocalHbm(uint32_t value) {
        if (mDisplayFd < 0) return;
        LocalHbmRequest request{.flag = 1, .displayId = 0, .value = value};
        if (ioctl(mDisplayFd.get(), kSetLocalHbm, &request) < 0) {
            PLOG(ERROR) << "Cannot set local HBM " << value;
        }
    }

    void releaseFinger() {
        setLocalHbm(kLocalHbmOffFingerUp);
        sendCommand(kNitCommand, 0);
        sendCommand(kPressCommand, 0);
    }

    fingerprint_device_t* mDevice = nullptr;
    android::base::unique_fd mTouchFd;
    android::base::unique_fd mDisplayFd;
};

UdfpsHandler* create() { return new KlimtUdfpsHandler(); }
void destroy(UdfpsHandler* handler) { delete handler; }

}  // namespace

extern "C" {
UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
    .create = create,
    .destroy = destroy,
};
}

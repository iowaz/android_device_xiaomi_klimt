// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <cstdint>
#include <limits>
#include <optional>

namespace klimt {
// Android expects positive charging current and negative discharge current.
constexpr std::optional<int32_t> NormalizeCurrent(int32_t raw) {
    if (raw == std::numeric_limits<int32_t>::min()) return std::nullopt;
    return -raw;
}
}  // namespace klimt

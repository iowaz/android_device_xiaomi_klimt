# SPDX-License-Identifier: Apache-2.0
"""Experimental HIS busy-photo fallback for the exact stock Klimt camera blob.

This bounds acquisition for photo mode 0x9002 using the vendor's existing
0x9200 timeout/disabled-node path. It does not repair the previous session's
hardware flush. Runtime recovery and stabilization remain to be validated.
"""
import hashlib
import struct
from pathlib import Path

STOCK_SHA256 = '5a1c4edc92d7a92c127bc9e9f08165a855b18ec73cbe8c7c0b3c7b1b1ea902a4'
PATCHED_SHA256 = '4a4fe8a0b6ca0bcc57b5045da37ca6ad64e52f4ee889a2dcefde23d09836329a'
STOCK_BUILD_ID = bytes.fromhex('7a25fc5b0de5dda4df78096b9598b1ee')
PATCH_BUILD_ID = hashlib.sha256(
    b'klimt-his-bounded-photo-v1\0' + bytes.fromhex(STOCK_SHA256)
).digest()[:16]


def _require(condition, message):
    if not condition:
        raise ValueError(f'HIS bounded-photo fixup: {message}')


def _branch(pc, target, conditional=False, condition=0):
    delta = target - pc
    bits = 19 if conditional else 26
    _require(delta % 4 == 0 and -(1 << (bits + 1)) <= delta < (1 << (bits + 1)),
             'branch target out of range')
    immediate = (delta // 4) & ((1 << bits) - 1)
    return (0x54000000 | (immediate << 5) | condition) if conditional else (0x14000000 | immediate)


def bounded_photo_blob(data):
    """Return guarded transformed bytes; exact already-patched input is a no-op."""
    digest = hashlib.sha256(data).hexdigest()
    if digest == PATCHED_SHA256:
        return data
    _require(digest == STOCK_SHA256, 'unsupported input SHA256')
    _require(data[:7] == b'\x7fELF\x02\x01\x01', 'not ELF64 little-endian')
    _require(struct.unpack_from('<H', data, 18)[0] == 183, 'not AArch64')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    phsize, count = struct.unpack_from('<HH', data, 54)
    _require((phoff, phsize, count) == (64, 56, 10), 'unexpected PHDR layout')
    _require(data[0x15418:0x15428] == bytes.fromhex('884240b9094092521f01096b81020054'),
             'unexpected operation-mode branch')
    headers = [list(struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize))
               for i in range(count)]
    _require(max(h[3] + h[6] for h in headers if h[0] == 1) == 0x4831c,
             'unexpected load mapping')
    base = 0x4c000
    new_count = count + 1
    stub = base + new_count * phsize
    code = struct.pack('<IIII', 0x52920049, 0x6b09011f,
                       _branch(stub + 8, 0x15428, True), _branch(stub + 12, 0x15474))
    size = new_count * phsize + len(code)
    result = bytearray(data)
    struct.pack_into('<Q', result, 32, base)
    struct.pack_into('<H', result, 56, new_count)
    struct.pack_into('<I', result, 0x15424, _branch(0x15424, stub, True, 1))
    # Make provenance distinguishable from stock while preserving NOTE layout.
    notes = [h for h in headers if h[0] == 4]
    _require(len(notes) == 1, 'unexpected NOTE count')
    note = notes[0]
    pos, end = note[2], note[2] + note[5]
    changed = False
    while pos < end:
        namesz, descsz, kind = struct.unpack_from('<III', data, pos)
        name = pos + 12
        desc = name + ((namesz + 3) & ~3)
        next_note = desc + ((descsz + 3) & ~3)
        _require(next_note <= end, 'invalid NOTE bounds')
        if data[name:name + namesz] == b'GNU\0' and kind == 3:
            _require(descsz == 16 and data[desc:desc + descsz] == STOCK_BUILD_ID,
                     'unexpected stock BuildId')
            result[desc:desc + descsz] = PATCH_BUILD_ID
            changed = True
        pos = next_note
    _require(changed, 'BuildId note missing')
    headers[0] = [6, 4, base, base, base, new_count * phsize, new_count * phsize, 8]
    headers.append([1, 5, base, base, base, size, size, 0x4000])
    _require(len(result) < base, 'new load overlaps existing file')
    result.extend(bytes(base - len(result)))
    result.extend(b''.join(struct.pack('<IIQQQQQQ', *h) for h in headers))
    result.extend(code)
    if PATCHED_SHA256 is not None:
        _require(hashlib.sha256(result).hexdigest() == PATCHED_SHA256,
                 'unexpected output SHA256')
    return bytes(result)


def fixup_his_bounded_photo(ctx, file, file_path, *args, **kwargs):
    path = Path(file_path)
    data = path.read_bytes()
    patched = bounded_photo_blob(data)
    if patched != data:
        path.write_bytes(patched)
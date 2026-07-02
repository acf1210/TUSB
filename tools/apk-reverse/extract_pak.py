import struct, os

path = "tools/apk-reverse/pak-extract/TONEX Control.pak"
out_dir = "tools/apk-reverse/pak-extract/assets"
data = open(path, "rb").read()

assert data[0:6] == b"IKMPAK"
version, count, flag, tableInfo = struct.unpack_from("<4I", data, 6)
print("version", version, "count", count, "flag", flag, "tableInfo", tableInfo)

pos = 6 + 16 + 4  # after magic + 4 u32 header fields + 4-byte pad
entries = []
for i in range(count):
    end = data.index(b"\x00", pos)
    name = data[pos:end].decode("utf-8", errors="replace")
    pos = end + 1
    offset, size = struct.unpack_from("<QQ", data, pos)
    pos += 16
    entries.append((name, offset, size))

print("parsed entries:", len(entries))
print("sample:", entries[:3])
print("last:", entries[-3:])

ok = 0
bad = 0
for name, offset, size in entries:
    if offset + size > len(data):
        bad += 1
        continue
    ok += 1
target = os.path.join(out_dir)
os.makedirs(target, exist_ok=True)
for name, offset, size in entries:
    if offset + size > len(data):
        continue
    dest = os.path.join(out_dir, name)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data[offset:offset+size])

print("ok:", ok, "bad:", bad)

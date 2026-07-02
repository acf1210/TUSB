import struct, re

path = "tools/apk-reverse/pak-extract/TONEX Control.pak"
data = open(path, "rb").read()
print("size:", len(data))
print("magic:", data[0:6])
print("header u32s:", struct.unpack_from("<8I", data, 6))

# Find all PNG signatures in the file (data blobs)
png_sig = b'\x89PNG\r\n\x1a\n'
png_offsets = [m.start() for m in re.finditer(re.escape(png_sig), data)]
print("PNG count:", len(png_offsets))
print("first 5 PNG offsets:", png_offsets[:5])

# Find filename-like ascii strings ending in known extensions
exts = [b'.png', b'.xml', b'.json', b'.ttf']
name_ends = []
for ext in exts:
    for m in re.finditer(re.escape(ext), data):
        name_ends.append(m.end())
print("total ext matches:", len(name_ends))

# Inspect bytes right after the first PNG-signature-containing region's preceding name end
# Try to find a filename string ending right before a length/offset pair
sample_end = None
for e in name_ends[:3]:
    start = max(0, e-80)
    chunk = data[start:e]
    # find last printable-run start
    print("---")
    print(repr(chunk[-60:]))
    print("bytes after ext:", data[e:e+24].hex())

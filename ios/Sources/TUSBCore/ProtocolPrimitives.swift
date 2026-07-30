import Foundation

public enum ProtocolDecodingError: Error, Equatable, Sendable {
    case truncated(expected: Int, available: Int)
    case invalid(String)
}

public enum CRC16X25 {
    public static func compute(_ bytes: [UInt8]) -> UInt16 {
        var crc: UInt16 = 0xFFFF
        for byte in bytes {
            crc ^= UInt16(byte)
            for _ in 0..<8 {
                crc = crc & 1 == 1 ? (crc >> 1) ^ 0x8408 : crc >> 1
            }
        }
        return crc ^ 0xFFFF
    }
}

public enum HDLCFrame: Equatable, Sendable {
    case valid([UInt8])
    case crcError
    case incomplete
}

public enum HDLC {
    private static let flag: UInt8 = 0x7E
    private static let escape: UInt8 = 0x7D

    public static func encode(_ payload: [UInt8]) -> [UInt8] {
        let crc = CRC16X25.compute(payload)
        let body = payload + [UInt8(truncatingIfNeeded: crc), UInt8(truncatingIfNeeded: crc >> 8)]
        var result: [UInt8] = [flag]
        result.reserveCapacity(body.count + 4)
        for byte in body {
            if byte == flag || byte == escape {
                result += [escape, byte ^ 0x20]
            } else {
                result.append(byte)
            }
        }
        result.append(flag)
        return result
    }

    public static func decode(_ stream: [UInt8]) -> HDLCFrame {
        guard let start = stream.firstIndex(of: flag),
              let end = stream[(start + 1)...].firstIndex(of: flag) else {
            return .incomplete
        }
        var unescaped: [UInt8] = []
        var index = start + 1
        while index < end {
            if stream[index] == escape {
                index += 1
                guard index < end else { return .incomplete }
                unescaped.append(stream[index] ^ 0x20)
            } else {
                unescaped.append(stream[index])
            }
            index += 1
        }
        guard unescaped.count >= 2 else { return .crcError }
        let payload = Array(unescaped.dropLast(2))
        let crc = UInt16(unescaped[unescaped.count - 2])
            | UInt16(unescaped[unescaped.count - 1]) << 8
        return crc == CRC16X25.compute(payload) ? .valid(payload) : .crcError
    }
}

public enum TaggedValue {
    public struct UInt16Result: Equatable, Sendable {
        public let value: UInt16
        public let nextOffset: Int

        public init(value: UInt16, nextOffset: Int) {
            self.value = value
            self.nextOffset = nextOffset
        }
    }

    public struct FloatResult: Equatable, Sendable {
        public let value: Float
        public let nextOffset: Int
    }

    public static func encodeU16(_ value: UInt16, tag: UInt8) -> [UInt8] {
        [tag, UInt8(truncatingIfNeeded: value), UInt8(truncatingIfNeeded: value >> 8)]
    }

    public static func decodeU16(_ bytes: [UInt8], offset: Int = 0) throws -> UInt16Result {
        try require(bytes, offset: offset, count: 3)
        return UInt16Result(
            value: UInt16(bytes[offset + 1]) | UInt16(bytes[offset + 2]) << 8,
            nextOffset: offset + 3
        )
    }

    public static func encodeFloat(_ value: Float) -> [UInt8] {
        let bits = value.bitPattern
        return [
            0x88,
            UInt8(truncatingIfNeeded: bits),
            UInt8(truncatingIfNeeded: bits >> 8),
            UInt8(truncatingIfNeeded: bits >> 16),
            UInt8(truncatingIfNeeded: bits >> 24),
        ]
    }

    public static func decodeFloat(_ bytes: [UInt8], offset: Int = 0) throws -> FloatResult {
        try require(bytes, offset: offset, count: 5)
        let bits = UInt32(bytes[offset + 1])
            | UInt32(bytes[offset + 2]) << 8
            | UInt32(bytes[offset + 3]) << 16
            | UInt32(bytes[offset + 4]) << 24
        return FloatResult(value: Float(bitPattern: bits), nextOffset: offset + 5)
    }

    static func require(_ bytes: [UInt8], offset: Int, count: Int) throws {
        guard offset >= 0, count >= 0, offset <= bytes.count, bytes.count - offset >= count else {
            throw ProtocolDecodingError.truncated(
                expected: max(0, offset) + count,
                available: bytes.count
            )
        }
    }
}

// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TUSB",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "TUSBCore", targets: ["TUSBCore"]),
    ],
    targets: [
        .target(name: "TUSBCore"),
        .testTarget(name: "TUSBCoreTests", dependencies: ["TUSBCore"]),
    ]
)

// swift-tools-version: 6.0
import Foundation
import PackageDescription

let swiftExportConfiguration: String = {
    let raw =
        ProcessInfo.processInfo.environment["SWIFT_EXPORT_CONFIGURATION"]
        ?? ProcessInfo.processInfo.environment["CONFIGURATION"]
        ?? "Debug"
    return raw.lowercased() == "release" ? "Release" : "Debug"
}()

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS(.v15),
    ],
    dependencies: [
        .package(name: "Lalrpop", path: "../../../build/SPMPackage/macosArm64/\(swiftExportConfiguration)"),
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "LalrpopLibrary", package: "Lalrpop"),
            ],
            linkerSettings: [
                .unsafeFlags(
                    [
                        "-L", "../../../build/swift-test/Debug",
                        "-lLalrpop",
                    ],
                    .when(configuration: .debug)
                ),
                .unsafeFlags(
                    [
                        "-L", "../../../build/swift-test/Release",
                        "-lLalrpop",
                    ],
                    .when(configuration: .release)
                ),
            ]
        ),
    ],
    swiftLanguageModes: [.v6]
)

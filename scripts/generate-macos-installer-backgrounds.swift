#!/usr/bin/env swift
import AppKit
import Foundation

func usage() {
    fputs("Usage: generate-macos-installer-backgrounds.swift <source-png> <light-out-png> <dark-out-png>\n", stderr)
}

guard CommandLine.arguments.count == 4 else {
    usage()
    exit(2)
}

let sourcePath = CommandLine.arguments[1]
let lightOutPath = CommandLine.arguments[2]
let darkOutPath = CommandLine.arguments[3]

let canvasWidth = 143
let canvasHeight = 222
let iconMaxSize: CGFloat = 128

func renderBackground(sourcePath: String, outputPath: String) throws {
    guard let source = NSImage(contentsOfFile: sourcePath) else {
        throw NSError(domain: "StackLens", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cannot read source image at \(sourcePath)"])
    }

    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: canvasWidth,
        pixelsHigh: canvasHeight,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bitmapFormat: [],
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else {
        throw NSError(domain: "StackLens", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot allocate bitmap"]) 
    }

    let srcSize = source.size
    let scale = min(iconMaxSize / max(srcSize.width, 1), iconMaxSize / max(srcSize.height, 1))
    let drawWidth = srcSize.width * scale
    let drawHeight = srcSize.height * scale
    let drawX = (CGFloat(canvasWidth) - drawWidth) / 2
    let drawY = (CGFloat(canvasHeight) - drawHeight) / 2
    let drawRect = NSRect(x: drawX, y: drawY, width: drawWidth, height: drawHeight)

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSColor.clear.setFill()
    NSRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight).fill()
    source.draw(in: drawRect, from: .zero, operation: .sourceOver, fraction: 1.0)
    NSGraphicsContext.restoreGraphicsState()

    guard let pngData = rep.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "StackLens", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot encode PNG"]) 
    }

    let outputURL = URL(fileURLWithPath: outputPath)
    let outputDir = outputURL.deletingLastPathComponent().path
    try FileManager.default.createDirectory(atPath: outputDir, withIntermediateDirectories: true)
    try pngData.write(to: outputURL, options: .atomic)
}

do {
    try renderBackground(sourcePath: sourcePath, outputPath: lightOutPath)
    try renderBackground(sourcePath: sourcePath, outputPath: darkOutPath)
    print("Generated installer backgrounds:")
    print("  \(lightOutPath)")
    print("  \(darkOutPath)")
} catch {
    fputs("Error: \(error.localizedDescription)\n", stderr)
    exit(1)
}

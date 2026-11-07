import Foundation
import Lalrpop
import Testing

@Suite("Lalrpop Swift Export")
struct LalrpopExportTests {
    @Test("ColorConfig and Level enums expose all expected cases")
    func enumsAndDefaults() {
        let defaultColor = ColorConfig.Companion.shared.default()
        #expect(defaultColor == ColorConfig.IfTty)
        #expect(ColorConfig.Yes != ColorConfig.No)
        #expect(ColorConfig.No != ColorConfig.IfTty)

        let levels: [Level] = [.Taciturn, .Informative, .Verbose, .Debug]
        #expect(levels.count == 4)
        #expect(Level.Taciturn != Level.Debug)
    }

    @Test("Configuration fluent builder configures session options")
    func configurationBuilderChain() {
        let cfg = api.Configuration.Companion.shared.new()
            .alwaysUseColors()
            .neverUseColors()
            .useColorsIfTty()
            .logInfo()
            .logVerbose()
            .logDebug()
            .logQuiet()
            .setMacroRecursionLimit(val: 128)
            .setFeatures(iterable: ["feature-alpha", "feature-beta"])
            .emitRerunDirectives(val: false)
            .emitComments(val: true)
            .emitWhitespace(val: true)
            .emitReport(val: true)
            .forceBuild(val: true)
        let fallbackCfg = api.Configuration.Companion.shared.default()
        #expect(cfg !== fallbackCfg)
    }

    @Test("End-to-end grammar compilation and incremental SHA3 cache skip via process()")
    func processDirectoryAndIncrementalRebuild() throws {
        let fm = FileManager.default
        let rootDir = fm.temporaryDirectory.appendingPathComponent("lalrpop-swift-\(UUID().uuidString)", isDirectory: true)
        let inDir = rootDir.appendingPathComponent("grammars", isDirectory: true)
        let outDir = rootDir.appendingPathComponent("generated", isDirectory: true)
        try fm.createDirectory(at: inDir, withIntermediateDirectories: true)
        try fm.createDirectory(at: outDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: rootDir) }

        let grammarSource = """
        grammar;

        pub Expr: i32 = {
            <l:Expr> "+" <r:Term> => l + r,
            Term,
        };

        Term: i32 = {
            r"[0-9]+" => i32::from_str(<>).unwrap(),
            "(" <Expr> ")",
        };
        """
        let grammarFile = inDir.appendingPathComponent("calc.lalrpop")
        try grammarSource.write(to: grammarFile, atomically: true, encoding: .utf8)

        api.Configuration.Companion.shared.new()
            .logQuiet()
            .neverUseColors()
            .forceBuild(val: true)
            .emitComments(val: true)
            .emitReport(val: true)
            .setInDir(dir: inDir.path)
            .setOutDir(dir: outDir.path)
            .process()

        let generatedRs = outDir.appendingPathComponent("calc.rs")
        let generatedReport = outDir.appendingPathComponent("calc.report")
        #expect(fm.fileExists(atPath: generatedRs.path))
        #expect(fm.fileExists(atPath: generatedReport.path))

        let rsContent = try String(contentsOf: generatedRs, encoding: .utf8)
        #expect(rsContent.contains("// auto-generated:"))
        #expect(rsContent.contains("// sha3:"))
        #expect(rsContent.contains("ExprParser"))

        let reportContent = try String(contentsOf: generatedReport, encoding: .utf8)
        #expect(!reportContent.isEmpty)

        // Append a marker comment while preserving the SHA3 header line:
        // forceBuild(false) should detect matching grammar SHA3 and skip overwriting.
        let marker = "\n// incremental-preserve-marker\n"
        try (rsContent + marker).write(to: generatedRs, atomically: true, encoding: .utf8)

        api.Configuration.Companion.shared.new()
            .logQuiet()
            .forceBuild(val: false)
            .setInDir(dir: inDir.path)
            .setOutDir(dir: outDir.path)
            .process()

        let afterIncremental = try String(contentsOf: generatedRs, encoding: .utf8)
        #expect(afterIncremental.contains("incremental-preserve-marker"))

        // Re-running with forceBuild(true) regenerates unconditionally and removes the marker.
        api.Configuration.Companion.shared.new()
            .logQuiet()
            .forceBuild(val: true)
            .setInDir(dir: inDir.path)
            .setOutDir(dir: outDir.path)
            .process()

        let afterForced = try String(contentsOf: generatedRs, encoding: .utf8)
        #expect(!afterForced.contains("incremental-preserve-marker"))
        #expect(afterForced.contains("ExprParser"))
    }

    @Test("processFile compiles conditional grammar with explicit feature flags")
    func processSingleFileWithFeatures() throws {
        let fm = FileManager.default
        let workDir = fm.temporaryDirectory.appendingPathComponent("lalrpop-swift-file-\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: workDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: workDir) }

        let grammarSource = """
        grammar;

        pub Token: &'input str = {
            #[cfg(feature = "alpha")]
            "alpha" => <>,
            "base" => <>,
        };
        """
        let grammarPath = workDir.appendingPathComponent("featured.lalrpop")
        try grammarSource.write(to: grammarPath, atomically: true, encoding: .utf8)

        api.Configuration.Companion.shared.new()
            .logQuiet()
            .forceBuild(val: true)
            .setFeatures(iterable: ["alpha"])
            .setOutDir(dir: workDir.path)
            .processFile(path: grammarPath.path)

        let generatedRs = workDir.appendingPathComponent("featured.rs")
        #expect(fm.fileExists(atPath: generatedRs.path))
        let rsText = try String(contentsOf: generatedRs, encoding: .utf8)
        #expect(rsText.contains("TokenParser"))
        #expect(rsText.contains("alpha"))
    }
}

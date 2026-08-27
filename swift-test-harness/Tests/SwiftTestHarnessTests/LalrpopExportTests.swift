import Testing
import Lalrpop

@Suite("Lalrpop Swift Export Tests")
struct LalrpopExportTests {
    @Test("Lalrpop Swift module imports cleanly")
    func swiftModuleLoads() {
        #expect(true)
    }
}

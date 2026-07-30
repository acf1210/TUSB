import XCTest

final class TUSBUITests: XCTestCase {
    func testDemoOpensAllMainTabs() {
        let app = XCUIApplication()
        app.launchArguments = ["--uitest-fake-pedal", "-AppleLanguages", "(pt-BR)", "-AppleLocale", "pt_BR"]
        app.launch()

        for tab in ["Editor", "Presets", "Ferramentas", "Menu"] {
            let button = app.tabBars.buttons[tab]
            XCTAssertTrue(button.waitForExistence(timeout: 3), "Missing \(tab) tab")
            button.tap()
        }
    }

    func testConnectScreenExplainsUSBAndStartsDemo() {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(pt-BR)", "-AppleLocale", "pt_BR"]
        app.launch()

        XCTAssertTrue(app.buttons["connect.usb"].waitForExistence(timeout: 3))
        app.buttons["connect.usb"].tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 2))
        app.alerts.firstMatch.buttons.firstMatch.tap()
        app.buttons["connect.demo"].tap()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 3))
    }
}

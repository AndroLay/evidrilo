import XCTest

@MainActor
final class EvidriloUITests: XCTestCase {
    func testGuestCanOpenManualProjectBasicsFromHome() {
        let app = XCUIApplication()
        app.launch()
        let entry = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR label CONTAINS[c] %@",
            "English", "Skip", "Home")).firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 30))
        if button(in: app, containing: "Continue").exists &&
            app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "English")).firstMatch.exists {
            button(in: app, containing: "Continue").tap()
        }
        let skip = button(in: app, containing: "Skip")
        if skip.waitForExistence(timeout: 3) { skip.tap() }
        let projects = button(in: app, containing: "Projects")
        XCTAssertTrue(projects.waitForExistence(timeout: 30))
        projects.tap()
        let create = button(in: app, containing: "Create a project")
        reveal(create, in: app)
        XCTAssertTrue(create.exists && create.isHittable)
        guard create.exists && create.isHittable else { return }
        create.tap()
        let blank = button(in: app, containing: "Start without a template")
        reveal(blank, in: app)
        XCTAssertTrue(blank.exists && blank.isHittable)
        guard blank.exists && blank.isHittable else { return }
        blank.tap()
        // Compose headings can be exported as accessibility "other" elements
        // on iOS. Assert the exact visible heading across element types.
        let basics = app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@", "Project basics")).firstMatch
        let openedBasics = basics.waitForExistence(timeout: 30)
        if !openedBasics {
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCTAssertTrue(openedBasics, "Manual project must open its Project basics heading")
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS[c] %@", "Project name")).firstMatch.exists)
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<6 {
            if element.exists && element.isHittable {
                // Compose scroll deceleration is not tracked by XCTest's idle
                // detection. A tap during it can stop scrolling instead of
                // activating the button. Wait for its actual frame to settle.
                var lastFrame = element.frame
                var stationarySince = Date()
                let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                    guard element.exists && element.isHittable else {
                        stationarySince = Date()
                        return false
                    }
                    let frame = element.frame
                    if frame != lastFrame {
                        lastFrame = frame
                        stationarySince = Date()
                    }
                    return Date().timeIntervalSince(stationarySince) >= 0.75
                }, object: nil)
                XCTAssertEqual(XCTWaiter.wait(for: [settled], timeout: 8), .completed,
                               "Button must stop scrolling before tapping")
                return
            }
            app.swipeUp()
        }
    }
    private func button(in app: XCUIApplication, containing label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch
    }
}

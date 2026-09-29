import XCTest

@MainActor
final class EvidriloUITests: XCTestCase {
    func testGuestCanOpenManualProjectBasicsFromHome() {
        let app = XCUIApplication()
        app.launch()

        let firstRoute = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@", "Skip for now", "My projects"))
            .firstMatch
        XCTAssertTrue(firstRoute.waitForExistence(timeout: 30))

        let skipOnboardingButton = button(in: app, containing: "Skip for now")
        if skipOnboardingButton.exists {
            if !skipOnboardingButton.isHittable {
                app.swipeUp()
            }
            skipOnboardingButton.tap()
        }

        XCTAssertTrue(app.staticTexts["My projects"].waitForExistence(timeout: 30))

        let continueProjectButton = button(in: app, containing: "Continue project")
        let createProjectButton = button(in: app, containing: "Create a project")
        let newProjectButton = button(in: app, containing: "New project")
        let projectAction: XCUIElement?
        if continueProjectButton.waitForExistence(timeout: 3) {
            projectAction = continueProjectButton
        } else if createProjectButton.waitForExistence(timeout: 20) {
            projectAction = createProjectButton
        } else if newProjectButton.waitForExistence(timeout: 20) {
            projectAction = newProjectButton
        } else {
            projectAction = nil
        }
        XCTAssertNotNil(projectAction)
        guard let projectAction else { return }

        if !projectAction.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(projectAction.isHittable)
        projectAction.tap()

        XCTAssertTrue(app.staticTexts["Project basics"].waitForExistence(timeout: 30))
    }

    private func button(in app: XCUIApplication, containing label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch
    }
}

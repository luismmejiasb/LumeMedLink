import XCTest

/// A tour of the patient's screens that saves a screenshot of each (task 0037): the author's way to see what is wired
/// without tapping through it. It drives a Debug build, so the data is the local platform's synthetic seed (ADR-0062)
/// or the design's script; never a person's (§9).
///
/// Run it with `Scripts/tour-screens.sh`, which boots the repo's own simulator, enrols its Face ID, and passes the
/// folder the pictures go to as `TEST_RUNNER_SCREENSHOT_DIR`. A step that cannot find its element still saves what
/// was on screen and the tour goes on, so one label that moved does not hide every screen after it.
final class ScreenTour: XCTestCase {
    private let app = XCUIApplication()
    private var shot = 0

    override func setUpWithError() throws {
        continueAfterFailure = true
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_CL"]
        app.launch()
    }

    func testTheWiredScreens() {
        snap("bienvenida")
        step("ingreso") { try tap("Ya tengo cuenta") }
        step("ingreso-datos") {
            try type("123456785", into: "RUT")
            try type("Clave1234!", into: "Contraseña")
            // The pinned «Ingresar» sits under the keyboard (ADR-0054); the last field's Return closes it.
            app.typeText("\n")
            settle()
            try tap("Ingresar")
        }
        step("ingreso-rapido") { try wait("Activar ingreso rápido") }
        step("inicio") { try tap("Activar ingreso rápido") }

        step("notificaciones") { try tapContaining("Notificaciones") }
        step("inicio-tras-notificaciones") { try tap("Volver") }

        step("buscar") { try tap("Buscar") }
        step("buscar-especialidad") { try tap("Especialidad") }
        step("buscar-comuna") {
            try type("Medicina interna", into: "Buscar especialidad")
            try tapButtonContaining("Medicina interna")
            try tap("Comuna")
        }
        step("buscar-con-filtros") {
            try type("Providencia", into: "Buscar comuna")
            try tapButtonContaining("Providencia")
        }
        step("resultados") { try tap("Buscar médicos") }
        step("agendar") { try tap("Agendar") }
        step("buscar-de-vuelta") {
            try tap("Volver")
            try tap("Volver")
        }

        step("citas-proximas") { try tap("Citas") }
        step("citas-pasadas") { try tap("Pasadas") }
        step("detalle-cita") { try tapFirstCard() }
        step("citas-de-vuelta") { try tap("Volver") }

        step("mis-medicos") { try tap("Mis médicos") }

        step("ajustes") { try tap("Ajustes") }
        step("cambiar-telefono") { try tap("Editar teléfono") }
        step("ajustes-de-vuelta") { try tap("Cancelar") }
    }

    // MARK: - Steps

    private struct Missing: Error { let label: String }

    /// Runs [action], then saves the screen as [name], whether the action found its element or not.
    private func step(_ name: String, _ action: () throws -> Void) {
        do {
            try action()
            settle()
            snap(name)
        } catch let missing as Missing {
            // What was on screen instead, for whoever reads the log: labels only, the data is synthetic (§9).
            print("TOUR missing \(missing.label) at \(name):\n\(app.debugDescription)")
            snap("\(name)-sin-\(missing.label)")
        } catch {
            snap("\(name)-error")
        }
    }

    private func element(_ label: String, exact: Bool = true) -> XCUIElement {
        let predicate = exact
            ? NSPredicate(format: "label == %@ OR identifier == %@", label, label)
            : NSPredicate(format: "label CONTAINS[cd] %@", label)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    @discardableResult
    private func wait(_ label: String, exact: Bool = true) throws -> XCUIElement {
        let found = element(label, exact: exact)
        guard found.waitForExistence(timeout: 15) else { throw Missing(label: label) }
        return found
    }

    private func tap(_ label: String) throws { touch(try wait(label)) }

    private func tapContaining(_ label: String) throws { touch(try wait(label, exact: false)) }

    private func type(_ text: String, into label: String) throws {
        touch(try wait(label))
        app.typeText(text)
    }

    /// Touches the element's middle. Compose hands XCUITest a button's text as a static text that is not "hittable",
    /// although the touch at its place reaches the button, as a finger's does.
    private func touch(_ element: XCUIElement) {
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    /// A row of a list, by part of its words: the search field above it carries the same words and is not a button.
    private func tapButtonContaining(_ label: String) throws {
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS[cd] %@", label)).firstMatch
        guard row.waitForExistence(timeout: 15) else { throw Missing(label: label) }
        touch(row)
    }

    /// The first appointment card of the list in front, named «doctor, day · hour»: it opens its detail anywhere.
    private func tapFirstCard() throws {
        let card = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", " · ")).firstMatch
        guard card.waitForExistence(timeout: 15) else { throw Missing(label: "cita") }
        touch(card)
    }

    /// Lets a push or a load finish: the skeleton is a screen too, but not the one to keep.
    private func settle() { Thread.sleep(forTimeInterval: 2.5) }

    private func snap(_ name: String) {
        shot += 1
        let image = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let folder = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] else { return }
        let file = URL(fileURLWithPath: folder).appendingPathComponent(String(format: "%02d-%@.png", shot, name))
        try? image.pngRepresentation.write(to: file)
    }
}

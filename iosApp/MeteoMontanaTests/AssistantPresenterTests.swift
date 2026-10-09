import XCTest
import Shared
@testable import MeteoMontana

/// Los textos y etiquetas del asistente salen de datos del servidor. Se
/// comprueban solo los trozos que no dependen del idioma del simulador
/// (números, grados, rangos), para que el test no cambie si el CI corre en inglés.
final class AssistantPresenterTests: XCTestCase {

    private func understood(
        gradeMin: String? = nil, gradeMax: String? = nil, discipline: String? = nil,
        km: Double? = nil, dateFrom: String? = nil, dateTo: String? = nil,
        beta: String? = nil, startType: String? = nil, withTopo: Bool = false, noRain: Bool = false,
        notDone: Bool = false, mostLines: Bool = false
    ) -> AssistantUnderstood {
        AssistantUnderstood(
            intent: "SEARCH", dateFrom: dateFrom, dateTo: dateTo,
            gradeMin: gradeMin, gradeMax: gradeMax, discipline: discipline,
            rockTypes: [], orientations: [],
            maxDistanceKm: km.map { KotlinDouble(double: $0) },
            q: nil, schoolMention: nil, sectorMention: nil, sun: nil, dayPart: nil,
            schoolMentions: [], topRated: false, minStars: nil, hoursAhead: nil,
            weatherTopic: nil, useMyLocation: false, beta: beta,
            startType: startType, withTopo: withTopo, noRain: noRain,
            mineTopic: nil, year: nil, notDone: notDone, action: nil,
            mostLines: mostLines)
    }

    func testSalidaTopoYSinLluviaSalenComoChips() {
        let chips = AssistantPresenter.chips(understood(startType: "SIT", withTopo: true, noRain: true),
                                             school: nil, sector: nil)
        XCTAssertTrue(chips.contains("Salida sentado"))
        XCTAssertTrue(chips.contains("Con topo"))
        XCTAssertTrue(chips.contains("Sin lluvia"))
    }

    func testUnaSolaEscuelaConVariosDiasDiceCualEsElMejor() {
        func day(_ d: String, _ score: Int32, rainy: Bool = false) -> AssistantDay {
            AssistantDay(date: d, score: score, rainMm: 0, rainProb: 0, rainy: rainy)
        }
        let card = AssistantSchoolCard(
            schoolId: "s", name: "Pedriza", rockType: nil, distanceKm: nil, lineCount: 10, combinedScore: KotlinInt(int: 70),
            days: [day("2026-10-09", 60), day("2026-10-10", 95, rainy: true), day("2026-10-11", 80)], rainDays: 1)
        let r = AssistantRecommendation(forecastAvailable: true,
                                        dates: ["2026-10-09", "2026-10-10", "2026-10-11"], schools: [card])
        let text = AssistantPresenter.recommendationIntro(r)
        XCTAssertTrue(text.contains("Pedriza"))
        XCTAssertTrue(text.contains("80"))          // se salta el día de más nota porque llueve
        XCTAssertFalse(text.contains("95"))
    }

    func testCompararSinGradoNoHablaDeEseGrado() {
        let card = AssistantSchoolCard(
            schoolId: "s", name: "Albarracín", rockType: nil, distanceKm: nil, lineCount: 61, combinedScore: KotlinInt(int: 26),
            days: [], rainDays: 0)
        let sinGrado = AssistantRecommendation(forecastAvailable: true, dates: ["2026-10-08"], schools: [card],
                                               byCount: false, filtered: false)
        let conGrado = AssistantRecommendation(forecastAvailable: true, dates: ["2026-10-08"], schools: [card],
                                               byCount: false, filtered: true)
        XCTAssertFalse(AssistantPresenter.compareIntro(sinGrado).contains("grado"))
        XCTAssertTrue(AssistantPresenter.compareIntro(conGrado).contains("grado"))
    }

    func testMasViasSaleComoChipYLaIntroDiceQueEsPorCantidad() {
        XCTAssertTrue(AssistantPresenter.chips(understood(mostLines: true), school: nil, sector: nil).contains("Con más vías"))
        let card = AssistantSchoolCard(
            schoolId: "s", name: "Albarracín", rockType: nil, distanceKm: nil, lineCount: 40, combinedScore: nil,
            days: [], rainDays: 0)
        let r = AssistantRecommendation(forecastAvailable: false, dates: [], schools: [card], byCount: true, filtered: true)
        let text = AssistantPresenter.recommendationIntro(r)
        XCTAssertFalse(text.contains("previsión"))      // no es "sin previsión": se pidió por cantidad
    }

    func testElPeriodoSaleEnLaRespuestaDeSuDiario() {
        let stats = MineAnswerer.MineAnswer(topic: "STATS", count: 5, maxGrade: "6C", lastDate: nil, place: nil,
                                            items: [], entries: [])
        let text = AssistantPresenter.mineText(stats, period: "17 sep – 8 oct")
        XCTAssertTrue(text.contains("17 sep – 8 oct"))
        XCTAssertTrue(text.contains("5"))
    }

    func testLoQueNoHeHechoSaleComoChip() {
        XCTAssertTrue(AssistantPresenter.chips(understood(notDone: true), school: nil, sector: nil).contains("Sin hacer"))
    }

    func testLasRespuestasSobreLoSuyoLlevanLasCifras() {
        let stats = MineAnswerer.MineAnswer(topic: "STATS", count: 12, maxGrade: "7A", lastDate: nil, place: nil, items: [], entries: [])
        let text = AssistantPresenter.mineText(stats)
        XCTAssertTrue(text.contains("12"))
        XCTAssertTrue(text.contains("7A"))
        let none = MineAnswerer.MineAnswer(topic: "LAST_VISIT", count: 0, maxGrade: nil, lastDate: nil, place: "Zarzalejo", items: [], entries: [])
        XCTAssertTrue(AssistantPresenter.mineText(none).contains("Zarzalejo"))
        let favs = MineAnswerer.MineAnswer(topic: "FAVORITES", count: 2, maxGrade: nil, lastDate: nil, place: nil,
                                           items: ["Albarracín", "La Pedriza"], entries: [])
        XCTAssertTrue(AssistantPresenter.mineText(favs).contains("Albarracín, La Pedriza"))
    }

    func testLaBetaSaleComoChipSegunLaAltura() {
        XCTAssertTrue(AssistantPresenter.chips(understood(beta: "ANY"), school: nil, sector: nil).contains("Con beta"))
        XCTAssertTrue(AssistantPresenter.chips(understood(beta: "TALL"), school: nil, sector: nil).contains("Beta +1,70"))
        XCTAssertTrue(AssistantPresenter.chips(understood(beta: "SHORT"), school: nil, sector: nil).contains("Beta -1,70"))
        XCTAssertFalse(AssistantPresenter.chips(understood(), school: nil, sector: nil).contains("Con beta"))
    }

    func testRangoDeGradosYDistanciaSalenComoChips() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "6A", gradeMax: "7A", km: 50),
                                             school: nil, sector: nil)
        XCTAssertTrue(chips.contains("6A – 7A"))
        XCTAssertTrue(chips.contains("< 50 km"))
    }

    func testUnSoloGradoNoSeRepite() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "7A", gradeMax: "7A"), school: nil, sector: nil)
        XCTAssertTrue(chips.contains("7A"))
        XCTAssertFalse(chips.contains("7A – 7A"))
    }

    func testEscuelaYSectorResueltosSalenPrimero() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "6A", gradeMax: "7A"),
                                             school: "Albarracín", sector: "Techos")
        XCTAssertEqual(Array(chips.prefix(2)), ["Albarracín", "Techos"])
    }

    func testRangoDeFechasDeUnMes() {
        let r = AssistantPresenter.dateRange("2026-12-05", "2026-12-08")
        XCTAssertTrue(r.hasPrefix("5 – 8 "), r)
    }

    func testUnSoloDiaNoLlevaRango() {
        let r = AssistantPresenter.dateRange("2026-12-05", "2026-12-05")
        XCTAssertTrue(r.hasPrefix("5 "), r)
        XCTAssertFalse(r.contains("–"), r)
    }

    func testEtiquetaDeDiaTerminaEnElNumero() {
        XCTAssertTrue(AssistantPresenter.dayLabel("2026-12-06").hasSuffix(" 6"))
        // Una fecha rota se devuelve tal cual, sin romper la pantalla.
        XCTAssertEqual(AssistantPresenter.dayLabel("no-es-fecha"), "no-es-fecha")
    }

    func testResumenDeSectorIncluyeLasCuentas() {
        let sector = AssistantSector(
            sectorId: "z1", name: "Techos", total: 5, matching: 2, unknownOrientation: 1,
            stones: [], unknownStones: [])
        let text = AssistantPresenter.sectorSummary(sector, sun: "SHADE")
        XCTAssertTrue(text.contains("2"), text)
        XCTAssertTrue(text.contains("1"), text)
    }

    func testPiedraSinOrientacionNoMuestraUnRumbo() {
        let sin = AssistantStone(blockId: "b", name: "P", aspect: nil, sun: nil, lineCount: 1, firstLineId: nil,
                                 photoPath: nil, linePath: nil, grade: nil, lineName: nil)
        let con = AssistantStone(blockId: "b", name: "P", aspect: "N", sun: "SHADE", lineCount: 1, firstLineId: "l1",
                                 photoPath: "f.jpg", linePath: "[{}]", grade: "7A", lineName: "La lágrima")
        XCTAssertFalse(AssistantPresenter.stoneLabel(sin).contains("N"), "sin dato no debe inventar un rumbo")
        XCTAssertTrue(AssistantPresenter.stoneLabel(con).hasPrefix("N"))
    }

    private func hit(_ school: String, _ name: String) -> LineSearchHit {
        LineSearchHit(
            schoolId: school, schoolName: "Escuela \(school)", blockId: "b-\(name)", blockName: "Piedra",
            lineId: "l-\(name)", lineName: name, grade: "6A", sectorName: nil, photoPath: nil, linePath: nil,
            startType: nil, lat: nil, lon: nil, orientation: nil, discipline: "BOULDER",
            rating: nil, ratingCount: nil)
    }

    func testAgrupaPorEscuelaConservandoElOrden() {
        let hits = [hit("b", "1"), hit("a", "2"), hit("b", "3"), hit("a", "4")]
        let groups = AssistantPresenter.groupHits(hits)
        XCTAssertEqual(groups.map { $0.id }, ["b", "a"])          // el primero en llegar va primero
        XCTAssertEqual(groups[0].shown.count, 2)
        XCTAssertEqual(groups[0].total, 2)
    }

    func testRecortaEscuelasYFilasPeroCuentaElTotal() {
        var hits: [LineSearchHit] = []
        for s in 0..<8 { for i in 0..<6 { hits.append(hit("s\(s)", "v\(s)-\(i)")) } }
        let groups = AssistantPresenter.groupHits(hits, maxSchools: 5, perSchool: 4)
        XCTAssertEqual(groups.count, 5)
        XCTAssertEqual(groups[0].shown.count, 4)
        XCTAssertEqual(groups[0].total, 6)                         // el total real, no el recortado
    }

    private func hit(_ school: String, _ name: String, sector: String?) -> LineSearchHit {
        LineSearchHit(
            schoolId: school, schoolName: "Escuela \(school)", blockId: "b-\(name)", blockName: "Piedra",
            lineId: "l-\(name)", lineName: name, grade: "6A", sectorName: sector, photoPath: nil, linePath: nil,
            startType: nil, lat: nil, lon: nil, orientation: nil, discipline: "BOULDER",
            rating: nil, ratingCount: nil)
    }

    func testUnaSolaEscuelaSeReparteEnSectoresYEnseñaMas() {
        var hits: [LineSearchHit] = []
        for i in 0..<10 { hits.append(hit("alb", "t\(i)", sector: "Techos")) }
        for i in 0..<5 { hits.append(hit("alb", "m\(i)", sector: "Mezquita")) }
        for i in 0..<2 { hits.append(hit("alb", "s\(i)", sector: nil)) }
        let g = AssistantPresenter.groupHits(hits)
        XCTAssertEqual(g.count, 1)
        XCTAssertEqual(g[0].shown.count, 17)                               // sola: no se recorta a 4
        XCTAssertEqual(g[0].sectors.map { $0.name }, ["Techos", "Mezquita", nil])   // más resultados primero, sin sector al final
        XCTAssertEqual(g[0].sectors[0].hits.count, 10)
    }

    func testConVariasEscuelasSeSiguenRecortandoPorEscuela() {
        var hits: [LineSearchHit] = []
        for s in ["a", "b"] { for i in 0..<9 { hits.append(hit(s, "\(s)\(i)", sector: "X")) } }
        let g = AssistantPresenter.groupHits(hits)
        XCTAssertEqual(g.map { $0.shown.count }, [4, 4])
        XCTAssertEqual(g.map { $0.total }, [9, 9])
    }

    func testGroupAllHitsNoRecortaNiEscuelasNiFilas() {
        var hits: [LineSearchHit] = []
        for s in 0..<8 { for i in 0..<9 { hits.append(hit("s\(s)", "v\(s)-\(i)", sector: "X")) } }
        let g = AssistantPresenter.groupAllHits(hits)
        XCTAssertEqual(g.count, 8)                                   // ninguna escuela se pierde
        XCTAssertEqual(g.map { $0.shown.count }, Array(repeating: 9, count: 8))   // ninguna fila se pierde
    }

    func testConMuchosResultadosSoloLaPrimeraEscuelaEmpiezaDesplegada() {
        var hits: [LineSearchHit] = []
        for s in ["a", "b", "c"] { for i in 0..<10 { hits.append(hit(s, "\(s)\(i)", sector: "X")) } }
        let g = AssistantPresenter.groupAllHits(hits)
        XCTAssertEqual(AssistantPresenter.initiallyExpanded(g), ["a"])
        XCTAssertEqual(AssistantPresenter.initiallyExpanded(g, limit: 40), ["a", "b", "c"])   // pocos: todas abiertas
    }

    func testLaClaveDeSectorDistingueEscuelas() {
        XCTAssertNotEqual(AssistantPresenter.sectorKey("a", "Techos"), AssistantPresenter.sectorKey("b", "Techos"))
    }

    func testAvisoDeMasResultadosSoloSiFaltan() {
        XCTAssertNil(AssistantPresenter.moreNote(total: 8, shown: 8))
        XCTAssertNotNil(AssistantPresenter.moreNote(total: 30, shown: 20))
        XCTAssertTrue(AssistantPresenter.moreNote(total: 30, shown: 20)!.contains("10"))
    }

    func testIntroDeResultadosCuentaLosHallazgos() {
        XCTAssertTrue(AssistantPresenter.hitsIntro(total: 12, needsLocationForDistance: false).contains("12"))
        XCTAssertFalse(AssistantPresenter.hitsIntro(total: 0, needsLocationForDistance: false).contains("0"))
    }

    func testAvisoDeAntiguedadSoloSiHaPasadoUnRato() {
        let ahora = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertNil(AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-600), now: ahora))
        let tres = AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-3 * 3600), now: ahora)
        XCTAssertNotNil(tres)
        XCTAssertTrue(tres!.contains("3"), tres!)
        // Entre media hora y una hora se dice "1 h", nunca "0 h".
        XCTAssertTrue(AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-2400), now: ahora)!.contains("1"))
    }

    // ── Estrellas ──

    private func rated(_ name: String, _ avg: Double?, _ votes: Int32?) -> LineSearchHit {
        LineSearchHit(
            schoolId: "s", schoolName: "Escuela", blockId: "b-\(name)", blockName: "Piedra",
            lineId: "l-\(name)", lineName: name, grade: "6A", sectorName: nil, photoPath: nil, linePath: nil,
            startType: nil, lat: nil, lon: nil, orientation: nil, discipline: "BOULDER",
            rating: avg.map { KotlinDouble(double: $0) }, ratingCount: votes.map { KotlinInt(int: $0) })
    }

    func testLasMejorValoradasVanPrimeroYLasSinVotosSeQuedanFuera() {
        let hits = [rated("a", 3.0, 5), rated("b", 4.8, 2), rated("sin", nil, nil), rated("c", 4.8, 9), rated("cero", 5.0, 0)]
        let out = AssistantPresenter.rankByRating(hits, minStars: nil).map { $0.lineName }
        XCTAssertEqual(out, ["c", "b", "a"])        // 4,8 con más votos antes que 4,8 con menos; sin votos fuera
    }

    func testElMinimoDeEstrellasFiltraPorLaMedia() {
        let hits = [rated("a", 3.0, 5), rated("b", 4.0, 2), rated("c", 4.5, 9)]
        XCTAssertEqual(AssistantPresenter.rankByRating(hits, minStars: 4).map { $0.lineName }, ["c", "b"])
        XCTAssertTrue(AssistantPresenter.rankByRating(hits, minStars: 5).isEmpty)
    }

    func testEmpatadasConservanElOrdenDeLlegada() {
        let hits = [rated("x", 4.0, 3), rated("y", 4.0, 3), rated("z", 4.0, 3)]
        XCTAssertEqual(AssistantPresenter.rankByRating(hits, minStars: nil).map { $0.lineName }, ["x", "y", "z"])
    }

    func testLasEstrellasLlevanMediaYVotos() {
        let s = AssistantPresenter.starsLabel(rating: 4.5, count: 8)
        XCTAssertTrue(s.contains("4") && s.contains("5") && s.contains("(8)"), s)
    }

    func testIntroDeMejorValoradasSinResultados() {
        XCTAssertFalse(AssistantPresenter.hitsIntro(total: 0, needsLocationForDistance: false, rated: true).isEmpty)
        XCTAssertTrue(AssistantPresenter.hitsIntro(total: 7, needsLocationForDistance: false, rated: true).contains("7"))
    }

    // ── El tiempo ──

    private func weather(topic: String = "GENERAL", expected: Bool = false, start: Int32? = nil,
                         mm: Double = 0, prob: Int32 = 10, wet: Bool = false,
                         days: [AssistantDayWeather] = []) -> AssistantWeather {
        AssistantWeather(
            placeName: nil, myLocation: true, topic: topic,
            now: AssistantNow(temperature: 14.4, humidity: 71, windKmh: 18.6, precipitationMm: 0,
                              rainProbability: 20, cloudCover: 60, dewPoint: KotlinDouble(double: 8.2)),
            hours: [AssistantHourPoint(time: "2026-10-08T15:00", temperature: 14, precipitationMm: 0,
                                       rainProbability: 10, windKmh: 16),
                    AssistantHourPoint(time: "2026-10-08T16:00", temperature: 12, precipitationMm: 1.2,
                                       rainProbability: 80, windKmh: 31)],
            rain: AssistantRain(expected: expected, startsInHours: start.map { KotlinInt(int: $0) },
                                totalMm: mm, maxProbability: prob, hoursChecked: 3,
                                startsAt: expected ? "2026-10-08T17:00" : nil, until: "2026-10-08T23:00"),
            climbing: AssistantClimbing(score: 62, label: "Aceptable", rockWet: wet, dryingMessage: nil,
                                        bestWindowStart: nil, bestWindowEnd: nil),
            days: days)
    }

    func testTemperaturaDeVariosDiasDaUnaLineaPorDiaConMinimaYMaxima() {
        let days = [
            AssistantDayWeather(date: "2026-10-09", tempMin: 5.2, tempMax: 14.4, precipitationMm: 0, score: 60, scoreLabel: "Bueno"),
            AssistantDayWeather(date: "2026-10-10", tempMin: 6.0, tempMax: 15.5, precipitationMm: 1.2, score: 48, scoreLabel: nil),
            AssistantDayWeather(date: "2026-10-11", tempMin: 7.0, tempMax: 16.0, precipitationMm: 0, score: 70, scoreLabel: "Bueno")
        ]
        let text = AssistantPresenter.weatherHeadline(weather(topic: "TEMPERATURE", days: days))
        let lines = text.components(separatedBy: "\n")
        XCTAssertEqual(lines.count, 4)                                   // título + un renglón por día
        XCTAssertTrue(lines[1].contains("5") && lines[1].contains("14"), text)
        XCTAssertTrue(lines[2].contains("6") && lines[2].contains("16"), text)   // 15,5 redondea a 16
        XCTAssertFalse(text.contains("ahora"), text)                    // no el "ahora" ni las horas de hoy
    }

    func testSinDiasLaTemperaturaSigueDiciendoAhoraYElRango() {
        let text = AssistantPresenter.weatherHeadline(weather(topic: "TEMPERATURE"))
        XCTAssertTrue(text.contains("12") && text.contains("14"), text)
    }

    func testSinLluviaDiceNoYHastaQueHoraSeHaMirado() {
        let t = AssistantPresenter.rainHeadline(weather(prob: 25))
        XCTAssertTrue(t.contains("23h") && t.contains("25"), t)          // "No… hasta las 23h… 25 %"
        XCTAssertTrue(t.hasPrefix("No"), t)                              // la respuesta empieza por SÍ o NO
    }

    func testConLluviaDiceSiLaHoraDeInicioYLaProbabilidad() {
        let t = AssistantPresenter.rainHeadline(weather(expected: true, start: 2, mm: 1.4, prob: 85))
        XCTAssertTrue(t.hasPrefix("Sí") || t.hasPrefix("Yes"), t)
        XCTAssertTrue(t.contains("17h") && t.contains("23h") && t.contains("85"), t)
        XCTAssertTrue(t.contains("1,4") || t.contains("1.4"), t)
    }

    func testLloviendoYaLoDiceAsi() {
        let t = AssistantPresenter.rainHeadline(weather(expected: true, start: 0, mm: 0.8, prob: 90))
        XCTAssertTrue(t.contains("23h"), t)
    }

    func testElVientoDestacaElMaximoDeLasProximasHoras() {
        let t = AssistantPresenter.weatherHeadline(weather(topic: "WIND"))
        XCTAssertTrue(t.contains("19") && t.contains("31"), t)      // ahora 18,6 → 19; máximo 31
    }

    func testLaHumedadDiceSiLaRocaEstaHumeda() {
        let seca = AssistantPresenter.weatherHeadline(weather(topic: "HUMIDITY", wet: false))
        let mojada = AssistantPresenter.weatherHeadline(weather(topic: "HUMIDITY", wet: true))
        XCTAssertTrue(seca.contains("71"), seca)
        XCTAssertNotEqual(seca, mojada)
    }

    func testElTituloSinSitioEsLaUbicacionDelUsuario() {
        XCTAssertFalse(AssistantPresenter.weatherTitle(weather()).isEmpty)
    }

    func testEtiquetaDeHoraYBarraDeLluvia() {
        XCTAssertEqual(AssistantPresenter.hourLabel("2026-10-08T15:00"), "15h")
        XCTAssertEqual(AssistantPresenter.rainBarHeight(mm: 0, full: 34), 0)
        XCTAssertEqual(AssistantPresenter.rainBarHeight(mm: 9, full: 34), 34)       // tope
        XCTAssertGreaterThanOrEqual(AssistantPresenter.rainBarHeight(mm: 0.1, full: 34), 3)   // un poco siempre se ve
    }

    func testSoloLosEstadosSinDatosTienenMensaje() {
        XCTAssertNil(AssistantPresenter.message(for: .ok))
        XCTAssertNil(AssistantPresenter.message(for: .needsInput))
        XCTAssertNotNil(AssistantPresenter.message(for: .busy))
        XCTAssertNotNil(AssistantPresenter.message(for: .userLimit))
        XCTAssertNotNil(AssistantPresenter.message(for: .unavailable))
    }
}

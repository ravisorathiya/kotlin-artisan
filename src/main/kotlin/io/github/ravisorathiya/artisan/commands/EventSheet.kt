package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * event/event_sheet.xlsx: the event.md tables as a spreadsheet for product / QA / managers.
 * artisan owns the file: it's rebuilt from event.md by make:event, make:permission, check:events, event:sheet and the
 * pre-commit hook, so it never drifts. A small OOXML writer, no dependency; the same event.md always gives the same
 * bytes, so git only sees a change when an event changed.
 */

internal const val EVENT_SHEET = "event_sheet.xlsx"

internal fun registerEventSheet() {
    Registry.add("event:sheet", "event:sheet", "Rebuild event/event_sheet.xlsx from event.md (Screen, Event name, Fired from, When)") { eventSheetCmd(it) }
}

internal class SheetRow(val screen: String, val name: String, val firedFrom: String, val whenText: String)
internal class SheetScreen(val name: String, val source: String, val rows: List<SheetRow>)

/** The screens of event.md with their source path (the `` `path` `` line under the heading) and rows split into cells. */
internal fun sheetScreens(doc: String): List<SheetScreen> {
    val parsed = parseEventDoc(doc)
    val sources = HashMap<String, String>()
    var screen: String? = null
    for (line in doc.lines()) {
        val heading = Regex("""^##\s+(.+?)\s*$""").find(line)
        if (heading != null) { screen = heading.groupValues[1]; continue }
        val path = Regex("""^`([^`]+\.(kt|java))`\s*$""").find(line) ?: continue
        screen?.let { sources.putIfAbsent(it, path.groupValues[1]) }
    }
    // a heading without event rows (the ScreenName placeholder, a prefix table) isn't a screen of the sheet
    return parsed.filter { it.key != "ScreenName" && it.value.isNotEmpty() }.map { (s, rows) ->
        SheetScreen(s, sources[s] ?: "", rows.map { (name, rest) ->
            val cells = rest.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
            if (cells.size >= 2) SheetRow(s, name, plain(cells[0]), plain(cells.drop(1).joinToString(" | ")))
            else SheetRow(s, name, "", plain(cells.firstOrNull() ?: ""))
        })
    }
}

/** Markdown cell -> plain text: no backticks, bold or links. */
private fun plain(s: String): String = s
    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
    .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    .replace("`", "")
    .replace("<br>", "\n")
    .trim()

/** The .xlsx bytes for [doc]: an Events tab (merged screen cells, frozen header, filter) and a Screens tab. */
internal fun eventSheetBytes(doc: String, app: String): ByteArray {
    val screens = sheetScreens(doc)
    val events = mutableListOf(listOf("No", "Screen", "Event name", "Fired from", "When"))
    val merges = mutableListOf<String>()
    var n = 0
    for (s in screens) {
        val first = events.size + 1
        s.rows.forEachIndexed { i, r -> events += listOf((++n).toString(), if (i == 0) s.name else "", r.name, r.firedFrom, r.whenText) }
        val last = events.size
        if (last > first) merges += "B$first:B$last"
    }
    val summary = listOf(listOf("Screen", "Source file", "Events")) + screens.map { listOf(it.name, it.source, it.rows.size.toString()) } +
        listOf(listOf("Total", "", n.toString()))

    val files = linkedMapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""",
        "docProps/core.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${xml("$app analytics events")}</dc:title><dc:creator>kotlin-artisan (generated from event.md, do not edit)</dc:creator></cp:coreProperties>""",
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Events" sheetId="1" r:id="rId1"/><sheet name="Screens" sheetId="2" r:id="rId2"/></sheets><definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">Events!${'$'}A${'$'}1:${'$'}E${'$'}${events.size}</definedName></definedNames></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
        // styles: 0 normal, 1 header (bold, white on blue), 2 wrapped top-aligned, 3 screen (bold, top-aligned)
        "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1F4E79"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFD0D0D0"/></left><right style="thin"><color rgb="FFD0D0D0"/></right><top style="thin"><color rgb="FFD0D0D0"/></top><bottom style="thin"><color rgb="FFD0D0D0"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
        "xl/worksheets/sheet1.xml" to sheetXml(events, listOf(6.0, 28.0, 32.0, 30.0, 70.0), merges, filter = true, boldCol = 1),
        "xl/worksheets/sheet2.xml" to sheetXml(summary, listOf(30.0, 70.0, 10.0), emptyList(), filter = false, boldCol = 0),
    )
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((path, text) in files) {
            // fixed time: the same event.md gives the same bytes
            zip.putNextEntry(ZipEntry(path).apply { time = 315576000000L })
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun sheetXml(rows: List<List<String>>, widths: List<Double>, merges: List<String>, filter: Boolean, boldCol: Int): String {
    val cols = widths.mapIndexed { i, w -> """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""" }.joinToString("")
    val last = ('A' + widths.size - 1)
    val data = rows.mapIndexed { r, row ->
        val cells = row.mapIndexed { c, v ->
            val ref = "${'A' + c}${r + 1}"
            val style = when { r == 0 -> 1; c == boldCol -> 3; else -> 2 }
            if (v.isEmpty()) """<c r="$ref" s="$style"/>"""
            else if (r > 0 && v.all { it.isDigit() } && v.length < 10) """<c r="$ref" s="$style"><v>$v</v></c>"""
            else """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${xml(v)}</t></is></c>"""
        }.joinToString("")
        """<row r="${r + 1}">$cells</row>"""
    }.joinToString("")
    val merge = if (merges.isEmpty()) "" else """<mergeCells count="${merges.size}">${merges.joinToString("") { """<mergeCell ref="$it"/>""" }}</mergeCells>"""
    val auto = if (filter) """<autoFilter ref="A1:$last${rows.size}"/>""" else ""
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>$cols</cols><sheetData>$data</sheetData>$auto$merge</worksheet>"""
}

private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    .filter { it == '\n' || it == '\t' || it >= ' ' }

/** The generated sheet next to [doc] (event/event.md). */
internal fun eventSheetFile(doc: File): File = File(doc.parentFile, EVENT_SHEET)

/** Writes the sheet for [docText] next to [doc] when its bytes changed. True when it wrote. */
internal fun writeEventSheet(doc: File, docText: String, app: String): Boolean {
    val f = eventSheetFile(doc)
    val bytes = eventSheetBytes(docText, app)
    if (f.isFile && f.readBytes().contentEquals(bytes)) return false
    f.parentFile.mkdirs()
    f.writeBytes(bytes)
    return true
}

/** Rebuilds event_sheet.xlsx from the project's event.md. The sheet when it changed, else null (also when there's no event.md). */
internal fun syncEventSheet(p: Project): File? {
    val doc = eventFile(p)?.takeIf { it.isFile } ?: return null
    return if (writeEventSheet(doc, doc.readText(), sheetAppName(p))) eventSheetFile(doc) else null
}

internal fun sheetAppName(p: Project): String = p.root.name

private fun eventSheetCmd(ctx: Ctx) {
    val p = ctx.project
    val doc = eventFile(p)?.takeIf { it.isFile } ?: throw CliError("No event/event.md here (artisan make:ai creates one).")
    val changed = writeEventSheet(doc, doc.readText(), sheetAppName(p))
    val screens = sheetScreens(doc.readText())
    val f = eventSheetFile(doc)
    Ui.say("${if (changed) "Wrote" else "Up to date:"} ${p.rel(f)}  (${screens.sumOf { it.rows.size }} event(s), ${screens.size} screen(s))", if (changed) Tone.OK else Tone.DIM)
    Ui.sayFile("Sheet".padEnd(8), f)
    Ui.say("  Generated from event.md: edit event.md, never the sheet (make:event, check:events and the git hook rebuild it).", Tone.DIM)
}

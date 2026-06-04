package com.example.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object DocxProcessor {

    /**
     * Writes plain text into a valid Microsoft Word (.docx) file.
     * Compliant with ECMA-376 / Office Open XML standard.
     */
    fun writeTextToDocx(text: String, outputFile: File) {
        val paragraphs = text.split("\n")
        
        // Generate word/document.xml
        val docXmlBuilder = StringBuilder()
        docXmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        docXmlBuilder.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n")
        docXmlBuilder.append("  <w:body>\n")
        
        for (paragraph in paragraphs) {
            val escapedText = escapeXml(paragraph)
            docXmlBuilder.append("    <w:p>\n")
            docXmlBuilder.append("      <w:r>\n")
            docXmlBuilder.append("        <w:t>$escapedText</w:t>\n")
            docXmlBuilder.append("      </w:r>\n")
            docXmlBuilder.append("    </w:p>\n")
        }
        
        docXmlBuilder.append("    <w:sectPr>\n")
        docXmlBuilder.append("      <w:pgSz w:w=\"11906\" w:h=\"16838\"/>\n") // A4 size in twentieths of a point (dxa)
        docXmlBuilder.append("    </w:sectPr>\n")
        docXmlBuilder.append("  </w:body>\n")
        docXmlBuilder.append("</w:document>")

        // Generate [Content_Types].xml
        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()

        // Generate _rels/.rels
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        // ZIP everything up to form a .docx package
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray())
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(relsXml.toByteArray())
            zos.closeEntry()

            // 3. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXmlBuilder.toString().toByteArray())
            zos.closeEntry()
        }
    }

    /**
     * Extracts all plain text lines from a Microsoft Word (.docx) file.
     */
    fun readDocxToText(file: File): String {
        if (!file.exists()) return ""
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return ""
                val text = zip.getInputStream(entry).use { input ->
                    readFully(input)
                }
                
                // Extract inner tags of type <w:t>...</w:t> safely using regex
                val regex = "<w:t[^>]*>(.*?)</w:t>".toRegex()
                val matches = regex.findAll(text)
                
                val result = StringBuilder()
                for (match in matches) {
                    val xmlText = match.groupValues[1]
                    result.append(unescapeXml(xmlText)).append("\n")
                }
                return result.toString().trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error reading document: ${e.localizedMessage}"
        }
    }

    private fun readFully(inputStream: InputStream): String {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            result.write(buffer, 0, length)
        }
        return result.toString("UTF-8")
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(input: String): String {
        return input.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}

package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawioSupportTest {
    @Test
    fun `extract returns null when the input contains no drawio block`() {
        assertNull(DrawioSupport.extract("just some prose, no XML"))
    }

    @Test
    fun `extract returns the inner mxfile from a fenced drawio code block`() {
        val xml = "<mxfile><diagram><mxGraphModel/></diagram></mxfile>"
        val text = "intro\n```drawio\n$xml\n```\nepilogue"
        assertEquals(xml, DrawioSupport.extract(text))
    }

    @Test
    fun `extract returns the bare mxfile when not in a fenced block`() {
        val xml = "<mxfile>x</mxfile>"
        val text = "Noise $xml noise"
        assertEquals(xml, DrawioSupport.extract(text))
    }

    @Test
    fun `extract falls back to mxGraphModel when neither mxfile nor fence is present`() {
        val model = "<mxGraphModel>data</mxGraphModel>"
        val text = "noise $model noise"
        assertEquals(model, DrawioSupport.extract(text))
    }

    @Test
    fun `sanitize strips Arrays-as-points block elements when paired`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<Array as=\"points\"><mxPoint x=\"0\"/></Array>" +
                "<mxCell id=\"2\"/></root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(!out.contains("Array"), "Array block should be stripped: $out")
    }

    @Test
    fun `sanitize removes self-closed mxPoint elements`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\"><mxGeometry><mxPoint/></mxGeometry></mxCell></root>" +
                "</mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(!out.contains("mxPoint"))
    }

    @Test
    fun `sanitize injects an as=geometry attribute when missing`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\"><mxGeometry width=\"10\"/></mxCell></root>" +
                "</mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("as=\"geometry\""))
    }

    @Test
    fun `sanitize keeps the as=geometry attribute if already present`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\"><mxGeometry as=\"geometry\" x=\"0\"/></mxCell></root>" +
                "</mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("as=\"geometry\""))
    }

    @Test
    fun `sanitize deduplicates mxCell ids by renaming duplicates`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\">a</mxCell><mxCell id=\"2\">b</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        val countOriginal = "<mxCell id=\"2\"".toRegex().findAll(out).count()
        val countRenamed = "id=\"sl-".toRegex().findAll(out).count()
        assertTrue(countOriginal <= 1, "duplicate id=2 should be removed: $countOriginal found")
        assertTrue(countRenamed >= 1, "expected at least one renamed duplicate id")
    }

    @Test
    fun `sanitize injects missing root cells id 0 and id 1`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root></root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("id=\"0\""))
        assertTrue(out.contains("id=\"1\""))
    }

    @Test
    fun `sanitize re-points non-special cells away from parent 0 to parent 1`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"42\" parent=\"0\"/>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("parent=\"1\""))
        assertTrue(!Regex("""id="0"\s+parent="0"""").containsMatchIn(out))
    }

    @Test
    fun `sanitize adds relative=1 to edge geometries missing it`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" edge=\"1\" source=\"0\" target=\"1\">" +
                "<mxGeometry x=\"0\" y=\"0\"/></mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("relative=\"1\""))
    }

    @Test
    fun `sanitize preserves existing relative=1 on edge geometries`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" edge=\"1\" source=\"0\" target=\"1\">" +
                "<mxGeometry relative=\"1\" x=\"0\"/></mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("relative=\"1\""))
    }

    @Test
    fun `sanitize normalizes hex colour values by prepending hash`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"fillColor=ff00aa\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("#ff00aa"))
    }

    @Test
    fun `sanitize keeps none and inherit colour values unchanged`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"fillColor=none;fontColor=inherit\">x</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("fillColor=none"))
        assertTrue(out.contains("fontColor=inherit"))
    }

    @Test
    fun `sanitize returns the input when there is no diagram wrapper to unwrap`() {
        val xml = "<mxGraphModel>x</mxGraphModel>"
        val out = DrawioSupport.sanitize(xml)
        assertNotNull(out)
        assertTrue(out.contains("mxGraphModel"))
    }

    @Test
    fun `sanitize normalizes style entries that have no equals sign (preserve as bare key)`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"rounded;fillColor=ff00aa\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("rounded"))
        assertTrue(out.contains("#ff00aa"))
    }

    @Test
    fun `sanitize keeps hash-prefixed colour values unchanged`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"fillColor=#ff00aa\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("fillColor=#ff00aa"))
    }

    @Test
    fun `sanitize keeps unknown colour values unchanged`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"fontColor=red\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("fontColor=red"))
    }

    @Test
    fun `sanitize normalizes all colour keys (fontColor, strokeColor, gradientColor, labelBackgroundColor, labelBorderColor)`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"fillColor=ff00aa;strokeColor=00ff00;fontColor=0000ff;gradientColor=ffff00;labelBackgroundColor=ff0000;labelBorderColor=00ffff\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("fillColor=#ff00aa"))
        assertTrue(out.contains("strokeColor=#00ff00"))
        assertTrue(out.contains("fontColor=#0000ff"))
        assertTrue(out.contains("gradientColor=#ffff00"))
        assertTrue(out.contains("labelBackgroundColor=#ff0000"))
        assertTrue(out.contains("labelBorderColor=#00ffff"))
    }

    @Test
    fun `sanitize does not normalize non-colour style keys`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" style=\"width=ff\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("width=ff"))
        assertTrue(!out.contains("width=#ff"))
    }

    @Test
    fun `sanitize re-points duplicate cell ids and preserves unique ids`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"5\">a</mxCell><mxCell id=\"5\">b</mxCell><mxCell id=\"5\">c</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        val originalCount = Regex("""\bid="5"""").findAll(out).count()
        assertTrue(originalCount <= 1, "expected at most one id=5, found $originalCount")
        val renamedCount = Regex("""\bid="sl-\d+"""").findAll(out).count()
        assertTrue(renamedCount >= 2, "expected at least 2 renamed cells, found $renamedCount")
    }

    @Test
    fun `sanitize skips parent re-pointing when no cell uses parent=0`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"42\" parent=\"1\"/>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("parent=\"1\""))
        assertTrue(out.contains("id=\"42\""))
    }

    @Test
    fun `sanitize does not re-point cell id 0 even when it has parent=0`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\" parent=\"0\"/>" +
                "<mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" parent=\"0\"/>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(!Regex("""id="0"\s+parent="0"""").containsMatchIn(out))
        assertTrue(Regex("""id="1"\s+parent="0"""").containsMatchIn(out))
    }

    @Test
    fun `sanitize keeps Array-as-points block intact when no Array element is present`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\">a</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("id=\"2\""))
        assertTrue(!out.contains("Array"))
    }

    @Test
    fun `sanitize keeps mxCell without id-attribute intact`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell>x</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("<mxCell>x</mxCell>") || out.contains("<mxCell >x</mxCell>"))
    }

    @Test
    fun `sanitize handles mxCell with parent=0 but no id attribute`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell parent=\"0\">x</mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("parent=\"1\""))
    }

    @Test
    fun `sanitize detects edge cell with no source or target attribute and adds relative=1`() {
        val xml =
            "<mxfile><diagram><mxGraphModel><root>" +
                "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" +
                "<mxCell id=\"2\" edge=\"1\">" +
                "<mxGeometry x=\"0\" y=\"0\"/></mxCell>" +
                "</root></mxGraphModel></diagram></mxfile>"
        val out = DrawioSupport.sanitize(xml)
        assertTrue(out.contains("relative=\"1\""))
    }
}

package com.stacklens.adapter.inbound.rest.pptx

import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle

@Component
class SprintReviewDeckBuilder(
    private val painter: PptxPainter = PptxPainter(),
    private val theme: PptxTheme = PptxTheme.DEFAULT,
) {
    fun build(): ByteArray {
        val show = XMLSlideShow()
        show.pageSize = Dimension(960, 540)
        try {
            painter.applyCustomTheme(show)

            val slide1 = show.createSlide()
            slide1.background.fillColor = theme.magenta
            painter.paintTitle(slide1, "Sprint 12 – Fulfillment Platform", titleColor = theme.neonYellow)
            painter.paintBody(
                slide1,
                titleTopY = 220,
                runs =
                    listOf(
                        PptxTextRun("Review-Zeitraum: 17.03. – 28.03.2026", "Courier New", 18f, false, false, Color.WHITE),
                        PptxTextRun("Team: Checkout, Payments, Notifications", "Arial Black", 16f, false, false, Color.WHITE),
                        PptxTextRun("Board-ID: 5 / Sprint 42", "Impact", 22f, true, false, theme.neonYellow),
                    ),
                bullet = true,
            )
            painter.paintShape(
                slide1,
                ShapeType.CHEVRON,
                Rectangle(820, 540, 220, 90),
                fill = theme.neonYellow,
                stroke = theme.magenta,
            )

            val slide2 = show.createSlide()
            slide2.background.fillColor = theme.turquoise
            painter.paintTitle(slide2, "Velocity & Auslastung", titleColor = Color.BLACK, accent = theme.orange)
            painter.paintBody(
                slide2,
                titleTopY = 220,
                runs =
                    listOf(
                        PptxTextRun("Committed Story-Points: 38", "Courier New", 20f, false, false, Color.BLACK),
                        PptxTextRun("Completed Story-Points: 41", "Courier New", 20f, true, false, Color.BLACK),
                        PptxTextRun("Completion-Rate: 108 %", "Arial Black", 22f, false, false, theme.orange),
                        PptxTextRun("Durchschnittliche PR-Größe: 247 LoC", "Courier New", 16f, false, true, theme.darkGray),
                    ),
                bullet = true,
            )
            painter.paintShape(slide2, ShapeType.ELLIPSE, Rectangle(120, 560, 80, 80), fill = theme.orange, stroke = Color.BLACK)
            painter.paintShape(slide2, ShapeType.TRIANGLE, Rectangle(280, 560, 80, 80), fill = theme.magenta, stroke = Color.BLACK)
            painter.paintShape(slide2, ShapeType.ROUND_RECT, Rectangle(440, 560, 160, 80), fill = theme.neonYellow, stroke = Color.BLACK)
            painter.paintBody(
                slide2,
                titleTopY = 470,
                runs =
                    listOf(
                        PptxTextRun(
                            "Frage: Welche drei Faktoren haben die Velocity am stärksten beeinflusst?",
                            "Comic Sans MS",
                            16f,
                            true,
                            false,
                            Color.BLACK,
                        ),
                    ),
                bullet = false,
            )

            val slide3 = show.createSlide()
            slide3.background.fillColor = theme.hotPink
            painter.paintTitle(slide3, "Risiken & Blocker", titleColor = theme.dangerRed)
            painter.paintBody(
                slide3,
                titleTopY = 220,
                runs =
                    listOf(
                        PptxTextRun(
                            "DEMO-14 (P1): Payment-Provider antwortet sporadisch mit 504",
                            "Arial Black",
                            18f,
                            false,
                            false,
                            theme.dangerRed,
                        ),
                        PptxTextRun(
                            "DEMO-17 (P2): Webhook-Doppelzustellung bei SendGrid-Retry",
                            "Arial Black",
                            18f,
                            false,
                            false,
                            theme.dangerRed,
                        ),
                        PptxTextRun(
                            "DEMO-22 (P3): Newsletter-Vorlagen-Tabelle fehlt im Staging",
                            "Courier New",
                            16f,
                            false,
                            true,
                            Color.WHITE,
                        ),
                    ),
                bullet = true,
            )
            painter.paintShape(slide3, ShapeType.TRIANGLE, Rectangle(820, 540, 120, 110), fill = theme.dangerRed, stroke = Color.BLACK)

            val slide4 = show.createSlide()
            slide4.background.fillColor = theme.orange
            painter.paintTitle(slide4, "Ausblick Sprint 13", titleColor = theme.navy, accent = Color.WHITE)
            painter.paintBody(
                slide4,
                titleTopY = 220,
                runs =
                    listOf(
                        PptxTextRun("Fokus-Themen Sprint 13:", "Arial Black", 20f, true, false, theme.navy),
                        PptxTextRun("Refactoring der Checkout-Route (Tech-Debt)", "Comic Sans MS", 16f, false, false, Color.BLACK),
                        PptxTextRun("Webhook-Signatur-Validierung produktiv rollouten", "Comic Sans MS", 16f, false, false, Color.BLACK),
                        PptxTextRun("GDPR-Audit der Newsletter-Subscription-Logs", "Comic Sans MS", 16f, false, false, Color.BLACK),
                    ),
                bullet = true,
            )
            painter.paintShape(slide4, ShapeType.STAR_5, Rectangle(120, 540, 100, 100), fill = theme.navy, stroke = Color.WHITE)
            painter.paintShape(slide4, ShapeType.RIGHT_ARROW, Rectangle(280, 560, 220, 60), fill = Color.WHITE, stroke = theme.navy)

            val png = PptxTheme.TINY_RED_PNG
            val picGreen = show.addPicture(png, PictureData.PictureType.PNG)
            val picRed = show.addPicture(png, PictureData.PictureType.PNG)
            val picBlue = show.addPicture(png, PictureData.PictureType.PNG)
            val picYellow = show.addPicture(png, PictureData.PictureType.PNG)
            slide1.createPicture(picGreen).setAnchor(Rectangle(100, 560, 80, 80))
            slide2.createPicture(picRed).setAnchor(Rectangle(660, 560, 80, 80))
            slide3.createPicture(picBlue).setAnchor(Rectangle(100, 560, 80, 80))
            slide4.createPicture(picYellow).setAnchor(Rectangle(560, 560, 80, 80))

            return show.toBytes()
        } finally {
            show.close()
        }
    }

    private fun XMLSlideShow.toBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        write(out)
        return out.toByteArray()
    }
}

package com.stacklens.adapter.inbound.rest.pptx

import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.springframework.stereotype.Component
import java.awt.Dimension
import java.awt.Rectangle

@Component
class BasicPptxFixtureBuilder {
    fun build(): ByteArray {
        val show = XMLSlideShow()
        show.pageSize = Dimension(960, 540)
        try {
            val slide1 = show.createSlide()
            val title1 = slide1.createTextBox()
            title1.text = "Sprint-Review"
            title1.setAnchor(Rectangle(120, 70, 1040, 80))
            val question1 = slide1.createTextBox()
            question1.text = "Wie war die Velocity?"
            question1.setAnchor(Rectangle(120, 260, 1040, 100))

            val slide2 = show.createSlide()
            val title2 = slide2.createTextBox()
            title2.text = "Ergebnisse"
            title2.setAnchor(Rectangle(120, 70, 1040, 80))
            val question2 = slide2.createTextBox()
            question2.text = "Welche Risiken gab es?"
            question2.setAnchor(Rectangle(120, 260, 1040, 100))

            val slide3 = show.createSlide()
            val pngBytes = PptxTheme.TINY_RED_PNG
            val pictureData = show.addPicture(pngBytes, PictureData.PictureType.PNG)
            slide3.createPicture(pictureData).setAnchor(Rectangle(480, 260, 320, 320))

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

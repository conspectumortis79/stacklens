package com.stacklens.domain.model

data class PptxThemeDto(
    val colors: Map<String, String>,
    val headingFont: String,
    val bodyFont: String,
    val backgroundColor: String?,
)

data class TextRunDto(
    val text: String,
    val fontFamily: String?,
    val fontSizePt: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val colorHex: String?,
    val bullet: Boolean,
)

sealed class PptxShapeDto {
    abstract val xPx: Int
    abstract val yPx: Int
    abstract val widthPx: Int
    abstract val heightPx: Int

    data class TextShape(
        override val xPx: Int,
        override val yPx: Int,
        override val widthPx: Int,
        override val heightPx: Int,
        val textRuns: List<TextRunDto>,
        val placeholderType: String?,
    ) : PptxShapeDto()

    data class PictureShape(
        override val xPx: Int,
        override val yPx: Int,
        override val widthPx: Int,
        override val heightPx: Int,
        val dataUri: String,
        val mime: String,
    ) : PptxShapeDto()

    data class TableShape(
        override val xPx: Int,
        override val yPx: Int,
        override val widthPx: Int,
        override val heightPx: Int,
        val rows: List<List<TextRunDto>>,
    ) : PptxShapeDto()

    data class FallbackImageShape(
        override val xPx: Int,
        override val yPx: Int,
        override val widthPx: Int,
        override val heightPx: Int,
        val dataUri: String,
    ) : PptxShapeDto()

    data class AutoShape(
        override val xPx: Int,
        override val yPx: Int,
        override val widthPx: Int,
        override val heightPx: Int,
        val fillColor: String?,
        val strokeColor: String?,
        val shapeType: String,
    ) : PptxShapeDto()
}

data class PptxSlideDto(
    val index: Int,
    val title: String?,
    val backgroundColor: String?,
    val shapes: List<PptxShapeDto>,
    val notes: String?,
    val rawText: String,
    val questionIds: List<String> = emptyList(),
    val answeredQuestions: Map<String, String> = emptyMap(),
)

data class PptxDeckDto(
    val slideWidthPx: Int,
    val slideHeightPx: Int,
    val theme: PptxThemeDto,
    val slides: List<PptxSlideDto>,
    val sourceFileName: String?,
)

data class PptxQuestion(
    val questionId: String,
    val slideIndex: Int,
    val shapeIndex: Int,
    val text: String,
)

data class PptxSlideQuestions(
    val slideIndex: Int,
    val questions: List<PptxQuestion>,
)

data class PptxQuestionPlan(
    val slides: List<PptxSlideQuestions>,
) {
    fun all(): List<PptxQuestion> = slides.flatMap { it.questions }

    fun isEmpty(): Boolean = slides.all { it.questions.isEmpty() }

    fun questionIds(): Set<String> = all().map { it.questionId }.toSet()
}

data class PptxAnswerSet(
    val answers: Map<String, String>,
) {
    fun isEmpty(): Boolean = answers.isEmpty()

    companion object {
        val EMPTY = PptxAnswerSet(emptyMap())
    }
}

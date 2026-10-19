package com.stacklens.testsupport.lucene

import org.apache.lucene.codecs.FieldsConsumer
import org.apache.lucene.codecs.FieldsProducer
import org.apache.lucene.codecs.FilterCodec
import org.apache.lucene.codecs.PostingsFormat
import org.apache.lucene.codecs.lucene104.Lucene104Codec
import org.apache.lucene.codecs.lucene104.Lucene104PostingsFormat
import org.apache.lucene.index.SegmentReadState
import org.apache.lucene.index.SegmentWriteState
import org.apache.lucene.index.Terms
import java.io.IOException

class UnreadableTermDictionaryCodec : FilterCodec(NAME, Lucene104Codec()) {
    private val unreadablePostingsFormat: PostingsFormat = UnreadableTermDictionaryPostingsFormat()

    override fun postingsFormat(): PostingsFormat = unreadablePostingsFormat

    private class UnreadableTermDictionaryPostingsFormat : PostingsFormat(POSTINGS_NAME) {
        private val delegate = Lucene104PostingsFormat()

        override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer = delegate.fieldsConsumer(state)

        override fun fieldsProducer(state: SegmentReadState): FieldsProducer = UnreadableFieldsProducer(delegate.fieldsProducer(state))
    }

    private class UnreadableFieldsProducer(
        private val delegate: FieldsProducer,
    ) : FieldsProducer() {
        override fun iterator(): MutableIterator<String> = delegate.iterator()

        override fun size(): Int = delegate.size()

        override fun terms(field: String): Terms = throw IOException("simulated unreadable term dictionary for field $field")

        override fun close() {
            delegate.close()
        }

        override fun checkIntegrity() {
            delegate.checkIntegrity()
        }

        override fun getMergeInstance(): FieldsProducer = delegate.mergeInstance
    }

    companion object {
        const val NAME = "StacklensUnreadableTerms104"
        const val POSTINGS_NAME = "StacklensUnreadablePostings104"
    }
}

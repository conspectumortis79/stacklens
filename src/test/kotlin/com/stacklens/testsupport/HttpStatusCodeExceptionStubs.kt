package com.stacklens.testsupport

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException

object HttpStatusCodeExceptionStubs {
    fun withoutResponseBody(
        status: HttpStatus = HttpStatus.BAD_GATEWAY,
        headers: HttpHeaders = HttpHeaders(),
    ): HttpStatusCodeException {
        val exception = mock<HttpClientErrorException>()
        whenever(exception.statusCode).thenReturn(status)
        whenever(exception.responseHeaders).thenReturn(headers)
        org.mockito.Mockito
            .doReturn(null)
            .`when`(exception)
            .responseBodyAsString
        return exception
    }
}

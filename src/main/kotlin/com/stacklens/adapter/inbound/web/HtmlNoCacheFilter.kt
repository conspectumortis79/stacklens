package com.stacklens.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class HtmlNoCacheFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = STATIC_ASSET.containsMatchIn(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        response.setHeader("Pragma", "no-cache")
        response.setHeader("Expires", "0")
        filterChain.doFilter(request, response)
    }

    private companion object {
        private val STATIC_ASSET = Regex("""\.(?:css|js|mjs|cjs|json|map|png|jpe?g|gif|webp|svg|ico|woff2?|ttf|eot)$""")
    }
}

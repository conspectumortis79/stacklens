package com.stacklens.application.service.loadtest

internal object RouteFileDetector {
    fun isRouteFileByName(
        path: String,
        name: String = path.substringAfterLast('/'),
    ): Boolean {
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")

        if (ext == "kt" || ext == "java") {
            val base = lower.substringBeforeLast('.')
            if (
                base.endsWith("controller") ||
                base.endsWith("resource") ||
                base.endsWith("endpoint")
            ) {
                return true
            }
        }

        return lower == "api.php" ||
            lower == "urls.py" ||
            lower == "router.js" ||
            lower == "router.ts" ||
            lower == "app.js" ||
            lower == "routes.php" ||
            path.startsWith("routes/") ||
            path.contains("/routes/") ||
            path.contains("routing") ||
            (lower.contains("router") && (ext == "kt" || ext == "java" || ext == "kts"))
    }

    fun isRouteFileWithContent(
        path: String,
        content: String,
    ): Boolean {
        if (isRouteFileByName(path)) return true
        if (content.isBlank()) return false
        val sample = content.take(2_000)
        return sample.contains("@RestController") ||
            sample.contains("@Controller") ||
            sample.contains("@RequestMapping") ||
            sample.contains("@GetMapping") ||
            sample.contains("@PostMapping") ||
            sample.contains("@PutMapping") ||
            sample.contains("@DeleteMapping") ||
            sample.contains("@PatchMapping") ||
            sample.contains("@Path(\"") ||
            sample.contains("@Path(") ||
            sample.contains("urlpatterns") ||
            sample.contains("router.post(") ||
            sample.contains("router.get(") ||
            sample.contains("router.put(") ||
            sample.contains("router.delete(") ||
            sample.contains("@app.route") ||
            sample.contains("@router.") ||
            sample.contains("app.get(") ||
            sample.contains("app.post(")
    }
}

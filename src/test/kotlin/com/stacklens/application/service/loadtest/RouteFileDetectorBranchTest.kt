package com.stacklens.application.service.loadtest

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteFileDetectorBranchTest {
    @Test
    fun `a spring rest controller kotlin file is detected by its suffix`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/OrderRestController.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/com/acme/OrderRestController.java"))
    }

    @Test
    fun `an endpoint or resource named kotlin file is detected`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/MetricResource.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/HealthEndpoint.kt"))
    }

    @Test
    fun `a kotlin file that is neither a controller nor a resource is not detected by name`() {
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/OrderService.kt"))
    }

    @Test
    fun `a router script with the kts extension is detected`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/routes/ExpressRouter.kts"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/routes/ExpressRouter.java"))
    }

    @Test
    fun `well known routing file names and paths are detected`() {
        assertTrue(RouteFileDetector.isRouteFileByName("routes/api.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("app/urls.py"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/router.js"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/router.ts"))
        assertTrue(RouteFileDetector.isRouteFileByName("app.js"))
        assertTrue(RouteFileDetector.isRouteFileByName("config/routes.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("routing/order.py"))
    }

    @Test
    fun `the explicit file name argument wins over the path`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/Anything.kt", name = "UserController.kt"))
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/com/acme/UserController.kt", name = "Anything.kt"))
    }

    @Test
    fun `an unrelated plain file is not detected`() {
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/resources/application.yml"))
    }

    @Test
    fun `a blank content sample never marks a file as a route file`() {
        assertFalse(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/com/acme/OrderService.kt", "   "))
    }

    @Test
    fun `spring mapping annotations in the content are detected`() {
        listOf(
            "@RestController",
            "@Controller",
            "@RequestMapping",
            "@GetMapping",
            "@PostMapping",
            "@PutMapping",
            "@DeleteMapping",
            "@PatchMapping",
        ).forEach { marker ->
            assertTrue(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/com/acme/OrderService.kt", "class X { $marker }"), marker)
        }
    }

    @Test
    fun `jax rs path annotations are detected with and without a literal`() {
        assertTrue(RouteFileDetector.isRouteFileWithContent("src/main/java/com/acme/OrderService.java", "@Path(\"/orders\")"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("src/main/java/com/acme/OrderService.java", "@Path(ORDERS_PATH)"))
    }

    @Test
    fun `python and javascript router declarations are detected`() {
        assertTrue(RouteFileDetector.isRouteFileWithContent("shop/serve.py", "urlpatterns = []"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "router.post('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "router.get('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "router.put('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "router.delete('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "@router.route('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "app.get('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.js", "app.post('/a')"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("web/main.py", "@app.route('/a')"))
    }

    @Test
    fun `plain business code is not detected by content`() {
        assertFalse(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/com/acme/Order.kt", "data class Order(val id: Long)"))
    }

    @Test
    fun `only the first two thousand characters are inspected`() {
        val padding = "x".repeat(2_100)
        assertFalse(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/com/acme/OrderService.kt", padding + "@GetMapping"))
        assertTrue(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/com/acme/OrderService.kt", padding.take(1_989) + "@GetMapping"))
    }
}

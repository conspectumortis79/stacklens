package com.stacklens.application.service.loadtest

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteFileDetectorTest {
    @Test
    fun `isRouteFileByName matches Spring Boot Kotlin controller`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../AdminController.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../LoadTestApiController.kt"))
    }

    @Test
    fun `isRouteFileByName matches Spring Boot Java controller`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/.../UserController.java"))
    }

    @Test
    fun `isRouteFileByName matches JAX-RS resource`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../UserResource.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/.../UserResource.java"))
    }

    @Test
    fun `isRouteFileByName matches Spring Boot actuator endpoint`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../HealthEndpoint.kt"))
    }

    @Test
    fun `isRouteFileByName matches Laravel routes files`() {
        assertTrue(RouteFileDetector.isRouteFileByName("routes/web.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("routes/api.php"))
    }

    @Test
    fun `isRouteFileByName matches PHP api_php`() {
        assertTrue(RouteFileDetector.isRouteFileByName("api.php"))
    }

    @Test
    fun `isRouteFileByName matches Django urls_py`() {
        assertTrue(RouteFileDetector.isRouteFileByName("project/urls.py"))
    }

    @Test
    fun `isRouteFileByName matches Express router files`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/router.js"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/router.ts"))
    }

    @Test
    fun `isRouteFileByName matches generic routing path`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/app/routing.py"))
    }

    @Test
    fun `isRouteFileByName does not match non-route Kotlin files`() {
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../UserService.kt"))
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../App.kt"))
        assertFalse(RouteFileDetector.isRouteFileByName("build.gradle.kts"))
    }

    @Test
    fun `isRouteFileByName does not match non-controller files containing word route`() {
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../RouteHelper.kt"))
    }

    @Test
    fun `isRouteFileByName is case-insensitive`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/ADMINCONTROLLER.KT"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/USERCONTROLLER.JAVA"))
    }

    @Test
    fun `isRouteFileWithContent matches Spring Kotlin file without Controller suffix`() {
        val content =
            """
            @Configuration
            @EnableWebMvc
            class WebConfig {
                @Bean
                fun viewController() = object {
                    @GetMapping("/api/foo")
                    fun foo() = "foo"
                }
            }
            """.trimIndent()
        assertTrue(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/.../WebConfig.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches FastAPI main_py with @router decorator`() {
        val content =
            """
            from fastapi import APIRouter
            router = APIRouter()

            @router.get("/items")
            def list_items():
                return []
            """.trimIndent()
        assertTrue(RouteFileDetector.isRouteFileWithContent("main.py", content))
    }

    @Test
    fun `isRouteFileWithContent matches Flask app_py with @app route decorator`() {
        val content =
            """
            from flask import Flask
            app = Flask(__name__)

            @app.route("/health")
            def health():
                return "ok"
            """.trimIndent()
        assertTrue(RouteFileDetector.isRouteFileWithContent("app.py", content))
    }

    @Test
    fun `isRouteFileWithContent rejects non-route Kotlin file`() {
        val content =
            """
            class UserService {
                fun findAll() = listOf<User>()
            }
            """.trimIndent()
        assertFalse(RouteFileDetector.isRouteFileWithContent("src/main/kotlin/.../UserService.kt", content))
    }

    @Test
    fun `isRouteFileWithContent rejects empty content`() {
        assertFalse(RouteFileDetector.isRouteFileWithContent("foo.kt", ""))
    }

    @Test
    fun `isRouteFileByName rejects file without extension that is not a route file`() {
        assertFalse(RouteFileDetector.isRouteFileByName("README"))
        assertFalse(RouteFileDetector.isRouteFileByName("LICENSE"))
    }

    @Test
    fun `isRouteFileByName matches RestController suffix`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/.../UserRestController.kt"))
    }

    @Test
    fun `isRouteFileByName matches routes-php file at top level`() {
        assertTrue(RouteFileDetector.isRouteFileByName("routes.php"))
    }

    @Test
    fun `isRouteFileByName matches router in java file`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/.../ApiRouter.java"))
    }

    @Test
    fun `isRouteFileByName matches router in kts file`() {
        assertTrue(RouteFileDetector.isRouteFileByName("build.router.kts"))
    }

    @Test
    fun `isRouteFileByName does not match random router-named file without recognized extension`() {
        assertFalse(RouteFileDetector.isRouteFileByName("router.txt"))
    }

    @Test
    fun `isRouteFileByName matches path with routing anywhere in it`() {
        assertTrue(RouteFileDetector.isRouteFileByName("internal/routing/config.py"))
    }

    @Test
    fun `isRouteFileByName matches app_js at top level`() {
        assertTrue(RouteFileDetector.isRouteFileByName("app.js"))
    }

    @Test
    fun `isRouteFileByName matches nested routes path`() {
        assertTrue(RouteFileDetector.isRouteFileByName("project/routes/web.php"))
    }

    @Test
    fun `isRouteFileByName uses path argument for subdirectory matching`() {
        assertTrue(RouteFileDetector.isRouteFileByName("routes/foo.txt", name = "foo.txt"))
    }

    @Test
    fun `isRouteFileByName rejects file with non-route name and no routing in path`() {
        assertFalse(RouteFileDetector.isRouteFileByName("utils.py"))
    }

    @Test
    fun `isRouteFileByName with non-kt-java extension bypasses suffix check`() {
        assertFalse(RouteFileDetector.isRouteFileByName("MyController.py"))
    }

    @Test
    fun `isRouteFileWithContent matches Spring Path annotation`() {
        val content = """@Path("/api/v1") class Foo {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("Foo.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring Path with double-quote argument`() {
        val content = """@Path("foo") class Bar {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("Bar.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Django urlpatterns`() {
        val content = """urlpatterns = [path('foo', views.foo)]"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("urls.py", content))
    }

    @Test
    fun `isRouteFileWithContent matches Express router-post`() {
        val content = """router.post("/foo", handler)"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("index.js", content))
    }

    @Test
    fun `isRouteFileWithContent matches Express router-get`() {
        val content = """router.get("/foo", handler)"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("index.js", content))
    }

    @Test
    fun `isRouteFileWithContent matches Express router-put`() {
        val content = """router.put("/foo", handler)"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("index.js", content))
    }

    @Test
    fun `isRouteFileWithContent matches Express router-delete`() {
        val content = """router.delete("/foo", handler)"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("index.js", content))
    }

    @Test
    fun `isRouteFileWithContent matches Express app-get and app-post`() {
        val content = """app.get("/foo", handler)\napp.post("/bar", handler)"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("server.js", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring PutMapping and PatchMapping`() {
        val content = """@PutMapping("/foo") fun a() {}\n@PatchMapping("/bar") fun b() {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("X.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring DeleteMapping`() {
        val content = """@DeleteMapping("/foo") fun a() {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("X.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring RestController annotation`() {
        val content = """@RestController class Foo {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("Foo.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring Controller annotation`() {
        val content = """@Controller class Foo {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("Foo.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring RequestMapping annotation`() {
        val content = """@RequestMapping("/api") class Foo {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("Foo.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring GetMapping annotation`() {
        val content = """@GetMapping("/foo") fun a() {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("X.kt", content))
    }

    @Test
    fun `isRouteFileWithContent matches Spring PostMapping annotation`() {
        val content = """@PostMapping("/foo") fun a() {}"""
        assertTrue(RouteFileDetector.isRouteFileWithContent("X.kt", content))
    }

    @Test
    fun `isRouteFileWithContent rejects file with no route signature and no recognized name`() {
        val content = """class A { fun b() = 1 }"""
        assertFalse(RouteFileDetector.isRouteFileWithContent("utils.py", content))
    }
}

package dev.kannich.core.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntPathMatcherTest {

    @Test
    fun `single star matches characters except slash`() {
        assertTrue(AntPathMatcher.matches("*.jar", "foo.jar"))
        assertTrue(AntPathMatcher.matches("*.jar", "bar.jar"))
        assertTrue(AntPathMatcher.matches("*.jar", ".jar"))
        assertFalse(AntPathMatcher.matches("*.jar", "foo/bar.jar"))
        assertFalse(AntPathMatcher.matches("*.jar", "foo.txt"))
    }

    @Test
    fun `double star matches characters including slash`() {
        assertTrue(AntPathMatcher.matches("**.jar", "foo.jar"))
        assertTrue(AntPathMatcher.matches("**.jar", "foo/bar.jar"))
        assertTrue(AntPathMatcher.matches("**.jar", "foo/bar/baz.jar"))
        assertFalse(AntPathMatcher.matches("**.jar", "foo.txt"))
    }

    @Test
    fun `question mark matches exactly one character`() {
        assertTrue(AntPathMatcher.matches("foo?.txt", "foo1.txt"))
        assertTrue(AntPathMatcher.matches("foo?.txt", "fooa.txt"))
        assertFalse(AntPathMatcher.matches("foo?.txt", "foo.txt"))
        assertFalse(AntPathMatcher.matches("foo?.txt", "foo12.txt"))
        assertFalse(AntPathMatcher.matches("foo?.txt", "foo/.txt"))
    }

    @Test
    fun `double star in path matches directories`() {
        val pattern = "target/**/classes"
        assertTrue(AntPathMatcher.matches(pattern, "target/classes"))
        assertTrue(AntPathMatcher.matches(pattern, "target/foo/classes"))
        assertTrue(AntPathMatcher.matches(pattern, "target/foo/bar/classes"))
        assertFalse(AntPathMatcher.matches(pattern, "src/classes"))
    }

    @Test
    fun `typical maven artifact pattern`() {
        val pattern = "**/target/*.jar"
        assertTrue(AntPathMatcher.matches(pattern, "target/foo.jar"))
        assertTrue(AntPathMatcher.matches(pattern, "module/target/foo.jar"))
        assertTrue(AntPathMatcher.matches(pattern, "a/b/target/app.jar"))
        assertTrue(AntPathMatcher.matches(pattern, "src/target/foo.jar")) // ** matches any prefix
        assertFalse(AntPathMatcher.matches(pattern, "target/classes/Foo.class"))
    }

    @Test
    fun `surefire reports pattern`() {
        val pattern = "**/target/surefire-reports/**"
        assertTrue(AntPathMatcher.matches(pattern, "target/surefire-reports/TEST-foo.xml"))
        assertTrue(AntPathMatcher.matches(pattern, "module/target/surefire-reports/TEST-foo.xml"))
        assertTrue(AntPathMatcher.matches(pattern, "target/surefire-reports/foo/bar.txt"))
        assertFalse(AntPathMatcher.matches(pattern, "target/failsafe-reports/TEST-foo.xml"))
    }

    @Test
    fun `exclude sources jar pattern`() {
        val pattern = "**/target/*-sources.jar"
        assertTrue(AntPathMatcher.matches(pattern, "target/foo-sources.jar"))
        assertTrue(AntPathMatcher.matches(pattern, "module/target/bar-sources.jar"))
        assertFalse(AntPathMatcher.matches(pattern, "target/foo.jar"))
        assertFalse(AntPathMatcher.matches(pattern, "target/sources.jar"))
    }

    @Test
    fun `no implicit directory shorthand`() {
        // Pattern ending in / does NOT automatically match subdirectories
        val pattern = "target/"
        assertFalse(AntPathMatcher.matches(pattern, "target/foo.jar"))
        assertTrue(AntPathMatcher.matches(pattern, "target/"))
    }

    @Test
    fun `matchesAny returns true if any pattern matches`() {
        val patterns = listOf("*.txt", "*.jar")
        assertTrue(AntPathMatcher.matchesAny(patterns, "foo.txt"))
        assertTrue(AntPathMatcher.matchesAny(patterns, "bar.jar"))
        assertFalse(AntPathMatcher.matchesAny(patterns, "baz.xml"))
    }

    @Test
    fun `regex special characters are escaped`() {
        assertTrue(AntPathMatcher.matches("foo.txt", "foo.txt"))
        assertFalse(AntPathMatcher.matches("foo.txt", "fooXtxt"))
        assertTrue(AntPathMatcher.matches("foo[1].txt", "foo[1].txt"))
        assertFalse(AntPathMatcher.matches("foo[1].txt", "foo1.txt"))
    }

    @Test
    fun `complex patterns with multiple wildcards`() {
        val pattern = "src/**/test/*Test.java"
        assertTrue(AntPathMatcher.matches(pattern, "src/test/FooTest.java"))
        assertTrue(AntPathMatcher.matches(pattern, "src/main/test/BarTest.java"))
        assertTrue(AntPathMatcher.matches(pattern, "src/a/b/c/test/BazTest.java"))
        assertFalse(AntPathMatcher.matches(pattern, "src/test/Foo.java"))
        assertFalse(AntPathMatcher.matches(pattern, "test/FooTest.java"))
    }
}

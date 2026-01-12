package dev.kannich.stdlib

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AntPathMatcherTest : FunSpec({

    test("single star matches characters except slash") {
        AntPathMatcher.matches("*.jar", "foo.jar") shouldBe true
        AntPathMatcher.matches("*.jar", "bar.jar") shouldBe true
        AntPathMatcher.matches("*.jar", ".jar") shouldBe true
        AntPathMatcher.matches("*.jar", "foo/bar.jar") shouldBe false
        AntPathMatcher.matches("*.jar", "foo.txt") shouldBe false
    }

    test("double star matches characters including slash") {
        AntPathMatcher.matches("**.jar", "foo.jar") shouldBe true
        AntPathMatcher.matches("**.jar", "foo/bar.jar") shouldBe true
        AntPathMatcher.matches("**.jar", "foo/bar/baz.jar") shouldBe true
        AntPathMatcher.matches("**.jar", "foo.txt") shouldBe false
    }

    test("question mark matches exactly one character") {
        AntPathMatcher.matches("foo?.txt", "foo1.txt") shouldBe true
        AntPathMatcher.matches("foo?.txt", "fooa.txt") shouldBe true
        AntPathMatcher.matches("foo?.txt", "foo.txt") shouldBe false
        AntPathMatcher.matches("foo?.txt", "foo12.txt") shouldBe false
        AntPathMatcher.matches("foo?.txt", "foo/.txt") shouldBe false
    }

    test("double star in path matches directories") {
        val pattern = "target/**classes"
        AntPathMatcher.matches(pattern, "target/classes") shouldBe true
        AntPathMatcher.matches(pattern, "target/foo/classes") shouldBe true
        AntPathMatcher.matches(pattern, "target/foo/bar/classes") shouldBe true
        AntPathMatcher.matches(pattern, "src/classes") shouldBe false
    }

    test("typical maven artifact pattern") {
        val pattern = "**target/*.jar"
        AntPathMatcher.matches(pattern, "target/foo.jar") shouldBe true
        AntPathMatcher.matches(pattern, "module/target/foo.jar") shouldBe true
        AntPathMatcher.matches(pattern, "a/b/target/app.jar") shouldBe true
        AntPathMatcher.matches(pattern, "src/target/foo.jar") shouldBe true
        AntPathMatcher.matches(pattern, "target/classes/Foo.class") shouldBe false
    }

    test("surefire reports pattern") {
        val pattern = "**target/surefire-reports/**"
        AntPathMatcher.matches(pattern, "target/surefire-reports/TEST-foo.xml") shouldBe true
        AntPathMatcher.matches(pattern, "module/target/surefire-reports/TEST-foo.xml") shouldBe true
        AntPathMatcher.matches(pattern, "target/surefire-reports/foo/bar.txt") shouldBe true
        AntPathMatcher.matches(pattern, "target/failsafe-reports/TEST-foo.xml") shouldBe false
    }

    test("exclude sources jar pattern") {
        val pattern = "**target/*-sources.jar"
        AntPathMatcher.matches(pattern, "target/foo-sources.jar") shouldBe true
        AntPathMatcher.matches(pattern, "module/target/bar-sources.jar") shouldBe true
        AntPathMatcher.matches(pattern, "target/foo.jar") shouldBe false
        AntPathMatcher.matches(pattern, "target/sources.jar") shouldBe false
    }

    test("no implicit directory shorthand") {
        val pattern = "target/"
        AntPathMatcher.matches(pattern, "target/foo.jar") shouldBe false
        AntPathMatcher.matches(pattern, "target/") shouldBe true
    }

    test("matchesAny returns true if any pattern matches") {
        val patterns = listOf("*.txt", "*.jar")
        AntPathMatcher.matchesAny(patterns, "foo.txt") shouldBe true
        AntPathMatcher.matchesAny(patterns, "bar.jar") shouldBe true
        AntPathMatcher.matchesAny(patterns, "baz.xml") shouldBe false
    }

    test("regex special characters are escaped") {
        AntPathMatcher.matches("foo.txt", "foo.txt") shouldBe true
        AntPathMatcher.matches("foo.txt", "fooXtxt") shouldBe false
        AntPathMatcher.matches("foo[1].txt", "foo[1].txt") shouldBe true
        AntPathMatcher.matches("foo[1].txt", "foo1.txt") shouldBe false
    }

    test("complex patterns with multiple wildcards") {
        val pattern = "src/**test/*Test.java"
        AntPathMatcher.matches(pattern, "src/test/FooTest.java") shouldBe true
        AntPathMatcher.matches(pattern, "src/main/test/BarTest.java") shouldBe true
        AntPathMatcher.matches(pattern, "src/a/b/c/test/BazTest.java") shouldBe true
        AntPathMatcher.matches(pattern, "src/test/Foo.java") shouldBe false
        AntPathMatcher.matches(pattern, "test/FooTest.java") shouldBe false
    }
})

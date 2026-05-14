@file:DependsOn("dev.kannich:kannich-test:0.10.0")
@file:DependsOn("dev.kannich:kannich-stdlib:0.10.0")
@file:DependsOn("dev.kannich:kannich-tools:0.10.0")
@file:DependsOn("dev.kannich:kannich-java:0.10.0")
@file:DependsOn("dev.kannich:kannich-maven:0.13.0")

import dev.kannich.java.Java
import dev.kannich.maven.Maven
import dev.kannich.test.*
import dev.kannich.tools.Fs

testSuite {
    beforeAll {
        clearCaches("tools/maven", "tools/java")
    }

    test("install maven works") {
        val java = Java("21")
        val maven = Maven("3.9.6", java)
        val result = maven.exec("--version")
        verify(result.success, "Maven installation failed.")
        verify(result.stdout.contains("3.9.6"), "Maven version is not 3.9.6")
    }

    test("maven creates settings.xml") {
        val java = Java("21")
        val maven = Maven("3.9.6", java) {
            server("some_server") {
                header("foo", "bar")
            }
        }
        maven.exec("--version")

        val settingsXml = Fs.readAsString(".kannich/settings.xml")
        verify(settingsXml.contains("<name>foo</name>"), "settings.xml does not contain header foo")
        verify(settingsXml.contains("<value>bar</value>"), "settings.xml does not contain header value bar")
    }
}

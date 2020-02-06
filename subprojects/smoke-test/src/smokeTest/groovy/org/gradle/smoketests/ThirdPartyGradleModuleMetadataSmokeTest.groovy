/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.smoketests

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.internal.os.OperatingSystem
import org.gradle.testkit.runner.BuildResult

class ThirdPartyGradleModuleMetadataSmokeTest extends AbstractSmokeTest {

    /**
     * Everything is done in one test to safe execution time.
     * Running the producer build takes ~2min.
     */
    @ToBeFixedForInstantExecution
    def 'produces expected metadata and can be consumed'() {

        given:
        BuildResult result
        def arch = OperatingSystem.current().macOsX ? 'MacosX64' : 'LinuxX64'
        useSample("gradle-module-metadata-example")
        def latestAndroidRelease = TestedVersions.androidGradle.versions[-2]

        def expectedRepo = new File(testProjectDir.root, 'producer/expected-repo')
        def actualRepo = new File(testProjectDir.root, 'producer/repo')

        when:
        buildFile = new File(testProjectDir.root, "producer/${defaultBuildFileName}.kts")
        replaceVariablesInBuildFile(
            kotlinVersion: TestedVersions.kotlin.versions.last(),
            androidPluginVersion: latestAndroidRelease)
        publish()

        then:
        expectedRepo.eachFileRecurse { expected ->
            def relativePath = expectedRepo.relativePath(expected)
            def actual = new File(actualRepo, relativePath)
            if (expected.name.endsWith('.pom')) {
                assert expected.text == actual.text : "content mismatch: $relativePath"
            }
            if (expected.name.endsWith('.module')) {
                compareJson(expected, actual, relativePath)
            }
        }

        when:
        buildFile = new File(testProjectDir.root, "consumer/${defaultBuildFileName}.kts")
        replaceVariablesInBuildFile(
            kotlinVersion: TestedVersions.kotlin.versions.last(),
            androidPluginVersion: latestAndroidRelease)
        result = consumer('java-app:run')

        then:
        result.output.trim() == """
            From java-library
            From kotlin-library
            From android-library
            From android-library (single variant)
            From android-kotlin-library
            From kotlin-multiplatform-library
            From kotlin-multiplatform-library (with Android variant)
        """.stripIndent().trim()

        when:
        result = consumer('kotlin-app:run')

        then:
        result.output.trim() == """
            From java-library
            From kotlin-library
            From android-library
            From android-library (single variant)
            From android-kotlin-library
            From kotlin-multiplatform-library
            From kotlin-multiplatform-library (with Android variant)
        """.stripIndent().trim()

        when:
        result = consumer('android-app:assembleFullDebug')

        then:
        result.output == ''

        when:
        result = consumer('android-kotlin-app:assembleFullDebug')

        then:
        result.output == ''

        when:
        result = consumer("native-app:runReleaseExecutable$arch")

        then:
        result.output.trim() == """
            From kotlin-multiplatform-library
            From kotlin-multiplatform-library (with Android variant)
        """.stripIndent().trim()
    }

    private BuildResult publish() {
        runner('publish', '--parallel').withProjectDir(
            new File(testProjectDir.root, 'producer')).forwardOutput().build()
    }

    private BuildResult consumer(String runTask) {
        runner(runTask, '-q').withProjectDir(
            new File(testProjectDir.root, 'consumer')).forwardOutput().build()
    }


    private static compareJson(File expected, File actual, String relativePath) {
        def actualJson = removeChangingDetails(new JsonSlurper().parseText(actual.text))
        def expectedJson = removeChangingDetails(new JsonSlurper().parseText(expected.text))
        assert actualJson == expectedJson : "content mismatch: $relativePath"
    }

    private static removeChangingDetails(moduleRoot) {
        moduleRoot.createdBy.gradle.version = ''
        moduleRoot.createdBy.gradle.buildId = ''
        moduleRoot.variants.each { it.files.each { it.size = '' } }
        moduleRoot.variants.each { it.files.each { it.sha512 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha256 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha1 = '' } }
        moduleRoot.variants.each { it.files.each { it.md5 = '' } }
        moduleRoot
    }
}

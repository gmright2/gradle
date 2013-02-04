/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule
/**
 * Tests maven POM customisation
 */
class MavenPublishPomCustomisationIntegTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def "can customise pom xml"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    mavenCustom(MavenPublication) {
                        pom.withXml {
                            asNode().groupId[0].value = "changed-group"
                            asNode().artifactId[0].value = "changed-artifact"
                            asNode().version[0].value = "changed-version"
                            asNode().appendNode('description', "custom-description")

                            def dependency = asNode().appendNode('dependencies').appendNode('dependency')
                            dependency.appendNode('groupId', 'junit')
                            dependency.appendNode('artifactId', 'junit')
                            dependency.appendNode('version', '4.11')
                            dependency.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('changed-group', 'changed-artifact', 'changed-version')
        module.assertPublished()
        module.parsedPom.description == 'custom-description'
        module.parsedPom.scopes.runtime.assertDependsOn("junit", "junit", "4.11")
    }

    def "pom and artifacts can contain non-ascii characters"() {
        // Group and Artifact are restricted to [A-Za-z0-9_\\-.]+ by org.apache.maven.project.validation.DefaultModelValidator
        def groupId = 'group'
        def artifactId = 'artifact'

        // Try version & description with non-ascii characters
        def version = 'version-₦ガき∆'
        def description = 'description-ç√∫'
        def extension = 'ext-₦ガき∆'
        def classifier = 'class-₦ガき∆'

        given:
        file("content-file") << "some content"
        settingsFile << "rootProject.name = '${artifactId}'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = '${groupId}'
            version = '${version}'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        pom.withXml {
                            asNode().appendNode('description', "${description}")
                        }
                        artifact file: "content-file", extension: "${extension}", classifier: "${classifier}"
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module(groupId, artifactId, version)
        module.assertPublished()
        module.parsedPom.description == description

        module.assertArtifactsPublished("${artifactId}-${version}.pom", "${artifactId}-${version}.jar", "${artifactId}-${version}-${classifier}.${extension}")
    }

    def "can generate pom file without publishing"() {
        given:
        settingsFile << "rootProject.name = 'generatePom'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    emptyMaven(MavenPublication) {
                        pom.withXml {
                            asNode().appendNode('description', "Test for pom generation")
                        }
                    }
                }
            }
            generatePomFileForEmptyMavenPublication {
                destination = 'build/generated-pom.xml'
            }
        """

        when:
        run "generatePomFileForEmptyMavenPublication"

        then:
        def mavenModule = mavenRepo.module("org.gradle.test", "generatePom", "1.0")
        mavenModule.assertNotPublished()

        and:
        file('build/generated-pom.xml').assertIsFile()
        def pom = new org.gradle.test.fixtures.maven.MavenPom(file('build/generated-pom.xml'))
        pom.groupId == "org.gradle.test"
        pom.artifactId == "generatePom"
        pom.version == "1.0"
        pom.description == "Test for pom generation"
    }

    def "has reasonable error message when withXml fails"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        pom.withXml {
                            asNode().foo = 'this is not a real element'
                        }
                    }
                }
            }
        """
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':generatePomFileForMavenPublication'")
        failure.assertHasCause("Could not apply withXml() to generated POM")
        failure.assertHasCause("No such property: foo for class: groovy.util.Node")
    }
}

val versionCoroutines: String by project
val versionHttpClient: String by project
val versionSlf4j: String by project

val versionCommonsCodec: String by project
val versionCommonsLang: String by project
val versionKoTest: String by project
val versionMockK: String by project
val versionWiremock: String by project

plugins {
    id("java-library")
    id("kotlin")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to  listOf("*.jar"))))
    implementation("com.squareup.okhttp3:okhttp:$versionHttpClient")
    implementation("org.slf4j:slf4j-simple:$versionSlf4j")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$versionCoroutines")

    testImplementation("commons-codec:commons-codec:$versionCommonsCodec")
    testImplementation("org.apache.commons:commons-lang3:$versionCommonsLang")
    testImplementation("io.kotest:kotest-runner-junit5:$versionKoTest")
    testImplementation("io.kotest:kotest-assertions-core:$versionKoTest")
    testImplementation("com.github.tomakehurst:wiremock:$versionWiremock")
    testImplementation("io.mockk:mockk:$versionMockK")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

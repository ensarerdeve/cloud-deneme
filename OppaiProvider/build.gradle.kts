version = 43

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

cloudstream {
    language = "en"
    description = "Ensar Stream video provider"
    authors = listOf("Ensar")
    status = 1
    tvTypes = listOf("Movie")
}

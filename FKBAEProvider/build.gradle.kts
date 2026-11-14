// use an integer for version numbers
version = 40

dependencies {
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "FKBAE video provider"
    authors = listOf("Ensar")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Anime",
        "AnimeMovie",
        "OVA",
    )

    iconUrl = "https://fkbae.to/wp-content/uploads/2019/04/fkbae-favicon-for-google.png"
}

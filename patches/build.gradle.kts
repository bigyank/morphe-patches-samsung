group = "app.bigyank"

patches {
    about {
        name = "Bigyank Samsung Patches"
        description = "Morphe patches for Samsung Health on Knox-tripped Galaxy devices"
        source = "git@github.com:bigyank/morphe-patches-samsung.git"
        author = "bigyank"
        contact = "https://github.com/bigyank/morphe-patches-samsung/issues"
        website = "https://github.com/bigyank/SamsungAppsPatcher"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    kotlin("jvm") version "2.3.10"
    idea
    id("com.gradleup.shadow") version "9.2.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = project.properties["group"]!!

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.xenondevs.xyz/releases") {
        name = "InvUI"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "CodeMC"
    }
    // Sided-IO：面配置库。本机开发时走 mavenLocal（先在 Sided-IO 里 publishToMavenLocal），
    // 发布后可以换成 GitHub Packages / Maven Central。
    mavenLocal {
        content {
            includeGroup("top.mc506lw.rebar.sidedio")
        }
    }
}

val rebarVersion = project.properties["rebar.version"] as String
val minecraftVersion = project.properties["minecraft.version"] as String
val sidedIoVersion = project.properties["sided-io.version"] as String

dependencies {
    // Paper Dev Bundle ( API + NMS，26.1+ )
    paperweight.paperDevBundle("$minecraftVersion.build.+")
    
    // Rebar
    compileOnly("io.github.pylonmc:rebar:$rebarVersion")
    
    // Sided-IO：对面配置（每面 INPUT/OUTPUT/BOTH/NONE）。运行时由服务器上的 Sided-IO 插件提供。
    compileOnly("top.mc506lw.rebar.sidedio:Sided-IO:$sidedIoVersion")
    
    // InvUI
    // 版本必须跟 Rebar 实际带的一致 —— InvUI 是 Rebar 的 paper-libraries 依赖，
    // 运行时用的是它那一份。之前这里写 2.1.1 而 Rebar 带 2.3.0，
    // 编译期看到的是旧接口，容易踩"编译过了运行炸"的坑。
    // （Rebar 0.44.4-26.2 的 paper-libraries.json 里是 invui 2.3.0。）
    compileOnly("xyz.xenondevs.invui:invui:2.3.0")
    
    compileOnly(kotlin("stdlib"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    
    options.release.set(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("25"))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier = ""
}

bukkit {
    name = project.name
    main = project.properties["main-class"] as String
    version = project.version.toString()
    apiVersion = "26.2"
    depend = listOf("Rebar", "Sided-IO")
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    
    authors = listOf("mc506lw")
}

paperweight {
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

tasks.register<Copy>("copyToServer") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar)
    into("D:\\我的世界资源库\\服务器\\岚域3.0\\plugins")
    outputs.upToDateWhen { false }
}

tasks.test {
    useJUnitPlatform()
}

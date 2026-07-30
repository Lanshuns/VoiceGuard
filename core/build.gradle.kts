import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

dependencies {
    labyProcessor()
    labyApi("api")

    // The VoiceChat API is supplied by the addon("voicechat") dependency declared in the root
    // build script, which labygradle resolves onto the compile classpath.
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}

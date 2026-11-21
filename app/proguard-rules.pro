# JNI: native methods are looked up by class and method name (ht102_usb.c).
-keep class io.github.mpridal.thermocamera.camera.IsoReader { *; }
-keep class io.github.mpridal.thermocamera.camera.IsoReader$Companion { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

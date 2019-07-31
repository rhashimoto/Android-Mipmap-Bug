# Android-Mipmap-Bug
The Android emulator 29.0.9 has a bug in GL_OES_EGL_image_external that can
break glGenerateMipmap(). This simple app demonstrates the bug. There is also
an instrumented test.

Nearly all of the files in the project are boilerplate. The interesting ones
are the [MainActivity](https://github.com/rhashimoto/Android-Mipmap-Bug/blob/master/app/src/main/java/com/example/glgeneratemipmap/MainActivity.kt)
and the [instrumented test](https://github.com/rhashimoto/Android-Mipmap-Bug/blob/master/app/src/androidTest/java/com/example/glgeneratemipmap/GenerateMipmap.kt).

Filed on the [Android issue tracker](https://issuetracker.google.com/issues/138741589).

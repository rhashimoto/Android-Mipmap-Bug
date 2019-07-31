package com.example.glgeneratemipmap

import android.opengl.EGL14.*
import android.opengl.EGLConfig
import android.opengl.GLES20.*
import android.renderscript.Matrix4f
import android.util.Log
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class GenerateMipmapTest {

  @Before
  fun setup() {
    val eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY)
    val version = IntArray(2)
    eglInitialize(eglDisplay, version, 0, version, 1)

    val configAttribs = intArrayOf(
      EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL_COLOR_BUFFER_TYPE, EGL_RGB_BUFFER,
      EGL_RED_SIZE, 8,
      EGL_GREEN_SIZE, 8,
      EGL_BLUE_SIZE, 8,
      EGL_ALPHA_SIZE, 8,
      EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
      EGL_NONE)
    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfig = IntArray(1)
    eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfig, 0)
    check(numConfig[0] > 0)

    val surfaceAttribs = intArrayOf(
      EGL_WIDTH, 1,
      EGL_HEIGHT, 1,
      EGL_NONE)
    val eglSurface = eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs, 0)

    val contextAttribs = intArrayOf(
      EGL_CONTEXT_CLIENT_VERSION, 2,
      EGL_NONE)
    val eglContext = eglCreateContext(eglDisplay, configs[0], EGL_NO_CONTEXT, contextAttribs, 0)
    eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
  }

  @Test
  fun testGenerateMipmapWithExternalTextureOES() {
    val TEXTURE_WIDTH = 256
    val TEXTURE_HEIGHT = 256
    val name = intArrayOf(0)

    // Create a unit square vertex buffer.
    glGenBuffers(1, name, 0)
    val bufferName = name[0]
    glBindBuffer(GL_ARRAY_BUFFER, bufferName)
    val buffer = byteArrayOf(
      0, 0,
      1, 0,
      0, 1,
      1, 1).let {
      ByteBuffer.allocateDirect(it.size)
        .put(it)
        .position(0)
    }
    glBufferData(GL_ARRAY_BUFFER, buffer.capacity(), buffer, GL_STATIC_DRAW)

    // Create a simple texturing shader.
    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, """
      attribute vec4 vertex;
      uniform mat4 transform;
      varying vec2 coord;
      void main() {
        coord = vertex.xy;
        gl_Position = transform * vertex;
      }""")
    glCompileShader(vertexShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, """
      precision mediump float;
      uniform sampler2D colorTexture;
      varying vec2 coord;
      void main() {
        gl_FragColor = texture2D(colorTexture, coord, 6.);
      }
    """)
    glCompileShader(fragmentShader)

    val shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, fragmentShader)
    glBindAttribLocation(shaderProgram, 0, "vertex")
    glLinkProgram(shaderProgram)

    // Create a program that uses samplerExternalOES.
    val oesShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(oesShader, """
      #extension GL_OES_EGL_image_external : require
      precision mediump float;
      uniform samplerExternalOES colorTexture;
      varying vec2 coord;
      void main() {
        gl_FragColor = texture2D(colorTexture, coord);
      }
    """)
    glCompileShader(oesShader)

    val oesProgram = glCreateProgram()
    glAttachShader(oesProgram, vertexShader)
    glAttachShader(oesProgram, oesShader)
    glBindAttribLocation(oesProgram, 0, "vertex")
    glLinkProgram(oesProgram)

    glValidateProgram(oesProgram)
    val status = intArrayOf(GL_FALSE)
    glGetProgramiv(oesProgram, GL_VALIDATE_STATUS, status, 0)
    if (status[0] != GL_TRUE) {
      Log.d("MainActivity", "validation: ${glGetProgramInfoLog(oesProgram)}")
    }

    // Create the texture we will render to.
    glGenTextures(1, name, 0)
    val textureName = name[0]
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, textureName)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      GL_RGBA,
      TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
      GL_RGBA, GL_UNSIGNED_BYTE,
      null)

    // Create a framebuffer that renders to the texture.
    glGenFramebuffers(1, name, 0)
    val framebufferName = name[0]
    glBindFramebuffer(GL_FRAMEBUFFER, framebufferName)
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureName, 0)

    // All objects are created.
    assertThat(glGetError()).isEqualTo(GL_NO_ERROR)

    // Render to texture via the framebuffer.
    // Just use clear to avoid having to define another shader.
    val screenViewport = IntArray(4)
    glGetIntegerv(GL_VIEWPORT, screenViewport, 0)
    glBindFramebuffer(GL_FRAMEBUFFER, framebufferName)
    glViewport(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)
    check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)

    // Fill the texture with green.
    glClearColor(0f, 1f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    // This is the line that triggers the bug on the emulator, even though we
    // undo it with the very next line.
    glUseProgram(oesProgram)
    glUseProgram(0)

    // Generate the mipmap. This where the emulator fails. Note that some platforms
    // (Linux) will record GL_INVALID_OPERATION and others (Mac) will not record an error.
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, textureName)
    glGenerateMipmap(GL_TEXTURE_2D)

    // Draw the texture we just rendered to the screen.
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glViewport(screenViewport[0], screenViewport[1], screenViewport[2], screenViewport[3])
    glUseProgram(shaderProgram)

    glBindBuffer(GL_ARRAY_BUFFER, bufferName)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 2, GL_UNSIGNED_BYTE, false, 0, 0)

    glUniformMatrix4fv(
      glGetUniformLocation(shaderProgram, "transform"),
      1, false,
      Matrix4f().apply {
        translate(-1f, -1f, 0f)
        scale(2f, 2f, 1f)
      }.array, 0)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

    // Read a pixel.
    val pixels = ByteBuffer.allocateDirect(4)
    glReadPixels(
      0, 0,
      1, 1,
      GL_RGBA, GL_UNSIGNED_BYTE,
      pixels)

    // The pixel should be green. It will be black if glGenerateMipmap()
    // failed (because the texture is not complete).
    val color = pixels.asIntBuffer().get(0)
    assertThat(color).isEqualTo(0x00ff00ff)
  }

  @After
  fun teardown() {
    val surface = eglGetCurrentSurface(EGL_DRAW)
    val context = eglGetCurrentContext()
    val display = eglGetCurrentDisplay()
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)
    eglDestroySurface(display, surface)
    eglDestroyContext(display, context)
    eglReleaseThread()
    eglTerminate(display)
  }
}
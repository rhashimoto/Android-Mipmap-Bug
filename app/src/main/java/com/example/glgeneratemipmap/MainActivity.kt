package com.example.glgeneratemipmap

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.renderscript.Matrix4f
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    surfaceView.setEGLContextClientVersion(2)
    surfaceView.setRenderer(Renderer())
    surfaceView.preserveEGLContextOnPause = true
  }
}

class Renderer : GLSurfaceView.Renderer {

  private var bufferName = 0
  private var shaderProgram = 0
  private var oesProgram = 0

  private var textureName = 0
  private var framebufferName = 0

  override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
    val name = intArrayOf(0)

    // Create a unit square vertex buffer.
    glGenBuffers(1, name, 0)
    bufferName = name[0]
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
    checkGL()

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

    shaderProgram = glCreateProgram()
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

    oesProgram = glCreateProgram()
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

    // Create the texture.
    glGenTextures(1, name, 0)
    textureName = name[0]
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, textureName)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      GL_RGBA,
      TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
      GL_RGBA, GL_UNSIGNED_BYTE,
      null)

    // Create a framebuffer that draws to the texture.
    glGenFramebuffers(1, name, 0)
    framebufferName = name[0]
    glBindFramebuffer(GL_FRAMEBUFFER, framebufferName)
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureName, 0)
    checkGL()
  }

  override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
    glViewport(0, 0, width, height)
  }

  override fun onDrawFrame(p0: GL10?) {
    // Render to texture via the framebuffer.
    // Just use clear to avoid having to define another shader.
    val screenViewport = IntArray(4)
    glGetIntegerv(GL_VIEWPORT, screenViewport, 0)
    glBindFramebuffer(GL_FRAMEBUFFER, framebufferName)
    check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)

    glViewport(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)

    glClearColor(0f, 1f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)
    glEnable(GL_SCISSOR_TEST)
    glScissor(0, TEXTURE_HEIGHT / 3, TEXTURE_WIDTH, TEXTURE_HEIGHT / 3)
    glClearColor(0f, 0f, 1f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)
    glDisable(GL_SCISSOR_TEST)
    checkGL()

    // This is where the bug happens! We bind this shader program, and even though
    // it is immediately unbound and never used for drawing, subsequently generating
    // a mipmap fails. If we instead bind a shader program that does *not* use
    // samplerExternalOES the bug does not appear.
    glUseProgram(oesProgram)
    glUseProgram(0)
    checkGL()

    // Generate the mipmap.
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, textureName)
    checkGL()
    glGenerateMipmap(GL_TEXTURE_2D)
    checkGL()

    // Draw the texture we just rendered to the screen.
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glViewport(screenViewport[0], screenViewport[1], screenViewport[2], screenViewport[3])
    glUseProgram(shaderProgram)

    glBindBuffer(GL_ARRAY_BUFFER, bufferName)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 2, GL_UNSIGNED_BYTE, false, 0, 0)

    // Draw to the top half of the screen without mipmaps enabled.
    glUniformMatrix4fv(
      glGetUniformLocation(shaderProgram, "transform"),
      1, false,
      Matrix4f().apply {
        translate(-1f, 0f, 0f)
        scale(2f, 1f, 1f)
      }.array, 0)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

    // Draw to the bottom half of the screen with mipmaps enabled. On
    // the emulator, this draws black and it appears that the texture
    // is not complete.
    glUniformMatrix4fv(
      glGetUniformLocation(shaderProgram, "transform"),
      1, false,
      Matrix4f().apply {
        translate(-1f, -1f, 0f)
        scale(2f, 1f, 1f)
      }.array, 0)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

    checkGL()
  }

  companion object {
    private const val TEXTURE_WIDTH = 256
    private const val TEXTURE_HEIGHT = 256
  }
}

fun convertGLErrorToString(error: Int): String {
    return when (error) {
        GL_NO_ERROR -> "GL_NO_ERROR"
        GL_INVALID_ENUM -> "GL_INVALID_ENUM"
        GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
        GL_INVALID_VALUE -> "GL_INVALID_VALUE"
        GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
        GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
        else -> "Unknown GL error $error"
    }
}

fun checkGL() {
    val error = glGetError()
    check(error == GL_NO_ERROR) { convertGLErrorToString(error) }
}

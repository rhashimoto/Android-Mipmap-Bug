package com.example.glgeneratemipmap

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
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
  override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
  }

  override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
    glViewport(0, 0, width, height)
  }

  override fun onDrawFrame(p0: GL10?) {
    glClearColor(1f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)
  }
}
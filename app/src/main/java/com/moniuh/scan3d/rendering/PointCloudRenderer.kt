package com.moniuh.scan3d.rendering

import android.opengl.GLES20
import com.moniuh.scan3d.core.PointCloud
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Renders the live scan preview as coloured, perspective-scaled points. */
class PointCloudRenderer {

    private var program = 0
    private var positionAttrib = 0
    private var colorAttrib = 0
    private var mvpUniform = 0
    private var pointSizeUniform = 0
    private var tintUniform = 0
    private val vbos = IntArray(2)
    private var count = 0
    private var uploadedVersion = -1

    /** 0 = true colours, 1 = fully tinted; tinting makes scanned coverage visible over the camera image. */
    var tint = 0.45f

    fun createOnGlThread() {
        program = ShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        colorAttrib = GLES20.glGetAttribLocation(program, "a_Color")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_MVP")
        pointSizeUniform = GLES20.glGetUniformLocation(program, "u_PointSize")
        tintUniform = GLES20.glGetUniformLocation(program, "u_Tint")
        GLES20.glGenBuffers(2, vbos, 0)
        uploadedVersion = -1
        count = 0
    }

    fun update(cloud: PointCloud?, version: Int) {
        if (version == uploadedVersion) return
        uploadedVersion = version
        if (cloud == null || cloud.size == 0) {
            count = 0
            return
        }
        val pos = ByteBuffer.allocateDirect(cloud.positions.size * 4).order(ByteOrder.nativeOrder())
        pos.asFloatBuffer().put(cloud.positions)
        val col = ByteBuffer.allocateDirect(cloud.colors.size).order(ByteOrder.nativeOrder())
        col.put(cloud.colors).position(0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cloud.positions.size * 4, pos, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cloud.colors.size, col, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        count = cloud.size
    }

    /** @param pointSizeAtOneMeter point diameter in pixels for a point one metre away. */
    fun draw(viewProjection: FloatArray, pointSizeAtOneMeter: Float) {
        if (count == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, viewProjection, 0)
        GLES20.glUniform1f(pointSizeUniform, pointSizeAtOneMeter)
        GLES20.glUniform1f(tintUniform, tint)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0])
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[1])
        GLES20.glVertexAttribPointer(colorAttrib, 3, GLES20.GL_UNSIGNED_BYTE, true, 0, 0)
        GLES20.glEnableVertexAttribArray(colorAttrib)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(colorAttrib)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 u_MVP;
            uniform float u_PointSize;
            attribute vec4 a_Position;
            attribute vec3 a_Color;
            varying vec3 v_Color;
            void main() {
                gl_Position = u_MVP * vec4(a_Position.xyz, 1.0);
                gl_PointSize = clamp(u_PointSize / max(gl_Position.w, 0.05), 2.0, 24.0);
                v_Color = a_Color;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform float u_Tint;
            varying vec3 v_Color;
            void main() {
                gl_FragColor = vec4(mix(v_Color, vec3(0.1, 0.85, 1.0), u_Tint), 1.0);
            }
        """
    }
}

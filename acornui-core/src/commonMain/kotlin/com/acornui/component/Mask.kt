/*
 * Copyright 2018 Poly Forest
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.acornui.component

import com.acornui.core.di.inject
import com.acornui.gl.core.Gl20
import com.acornui.gl.core.GlState
import com.acornui.gl.core.ShaderBatch
import com.acornui.gl.core.setScissor
import com.acornui.math.IntRectangle
import com.acornui.math.Vector3
import kotlin.math.abs
import kotlin.math.roundToInt

object StencilUtil {

	var depth = -1

	inline fun mask(batch: ShaderBatch, gl: Gl20, renderMask: () -> Unit, renderContents: () -> Unit) {
		batch.flush()
		depth++
		if (depth >= 65535) throw IllegalStateException("There may not be more than 65535 nested masks.")
		if (depth == 0) {
			gl.enable(Gl20.STENCIL_TEST)
		}
		gl.colorMask(false, false, false, false)
		gl.stencilFunc(Gl20.ALWAYS, 0, 0.inv())
		gl.stencilOp(Gl20.INCR, Gl20.INCR, Gl20.INCR)
		renderMask()
		batch.flush()

		gl.colorMask(true, true, true, true)
		gl.stencilFunc(Gl20.EQUAL, depth + 1, 0.inv())
		gl.stencilOp(Gl20.KEEP, Gl20.KEEP, Gl20.KEEP)

		renderContents()

		batch.flush()
		gl.colorMask(false, false, false, false)
		gl.stencilFunc(Gl20.ALWAYS, 0, 0.inv())
		gl.stencilOp(Gl20.DECR, Gl20.DECR, Gl20.DECR)
		renderMask()

		batch.flush()
		gl.colorMask(true, true, true, true)
		gl.stencilFunc(Gl20.EQUAL, depth, 0.inv())
		gl.stencilOp(Gl20.KEEP, Gl20.KEEP, Gl20.KEEP)

		if (depth == 0) {
			gl.disable(Gl20.STENCIL_TEST)
		}
		depth--
	}
}


/**
 * Calls scissorLocal with the default rectangle of 0, 0, width, height
 */
fun UiComponentRo.scissorLocal(inner: () -> Unit) {
	scissorLocal(0f, 0f, width, height, inner)
}

/**
 * Wraps the [inner] call in a scissor rectangle.
 * The local coordinates will be converted to gl window coordinates automatically.
 * Note that this will not work properly for rotated components.
 */
fun UiComponentRo.scissorLocal(x: Float, y: Float, width: Float, height: Float, inner: () -> Unit) {
	val tmp = Vector3.obtain()
	localToCanvas(tmp.set(x, y, 0f))
	val sX1 = tmp.x
	val sY1 = tmp.y
	localToCanvas(tmp.set(width, height, 0f))
	val sX2 = tmp.x
	val sY2 = tmp.y
	Vector3.free(tmp)

	val glState = inject(GlState)
	val intR = IntRectangle.obtain()
	glState.getViewport(intR)
	glState.setScissor(
			minOf(sX1, sX2).roundToInt(),
			(intR.height - maxOf(sY1, sY2)).roundToInt(),
			abs(sX2 - sX1).roundToInt(),
			abs(sY2 - sY1).roundToInt(),
			inner
	)
	IntRectangle.free(intR)
}
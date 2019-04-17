package com.acornui.core

import com.acornui.component.canvasToLocal
import com.acornui.component.localToCanvas
import com.acornui.graphic.ColorRo
import com.acornui.math.Matrix4Ro
import com.acornui.math.MinMax
import com.acornui.math.MinMaxRo

interface Renderable {

	/**
	 * Calculates the region, in local coordinates, that this component will draw.
	 */
	fun drawRegion(out: MinMax): MinMax

	/**
	 * Renders any graphics.
	 *
	 * Canvas coordinates are 0,0 top left, and bottom right is the canvas width/height without dpi scaling.
	 *
	 * You may convert the window coordinate clip region to local coordinates via [canvasToLocal], but in general it is
	 * faster to convert the local coordinates to window coordinates [localToCanvas], as no matrix inversion is
	 * required.
	 *
	 * @param clip The visible region (in viewport coordinates.) If you wish to render a component with a no
	 * clipping, you may use [MinMaxRo.POSITIVE_INFINITY]. This is used in order to potentially avoid drawing things
	 * the user cannot see. (Due to the screen size, stencil buffers, or scissors)
	 *
	 * @param transform The world transformation to use for drawing the target.
	 *
	 * @param tint The final tint to use for vertices.
	 */
	fun render(
			clip: MinMaxRo,
			transform: Matrix4Ro,
			tint: ColorRo
	)
}
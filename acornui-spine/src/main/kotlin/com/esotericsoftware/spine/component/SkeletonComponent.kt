/*
 * Copyright 2015 Nicholas Bilyk
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

package com.esotericsoftware.spine.component

import com.acornui.component.ComponentInit
import com.acornui.gl.core.*
import com.acornui.graphic.Color
import com.acornui.graphic.ColorRo
import com.acornui.math.*
import com.esotericsoftware.spine.Skeleton
import com.esotericsoftware.spine.Slot
import com.esotericsoftware.spine.animation.AnimationState
import com.esotericsoftware.spine.animation.AnimationStateData
import com.esotericsoftware.spine.attachments.MeshAttachment
import com.esotericsoftware.spine.attachments.RegionAttachment
import com.esotericsoftware.spine.attachments.SkeletonAttachment
import com.esotericsoftware.spine.attachments.WeightedMeshAttachment
import com.esotericsoftware.spine.component.SpineVertexUtils.positionOffset
import com.esotericsoftware.spine.renderer.SkeletonMeshRenderer
import com.esotericsoftware.spine.renderer.SkeletonRenderer

/**
 * An encapsulation of the parts needed to animate and render a single skeleton.
 */
open class SkeletonComponent(
		val loadedSkeleton: LoadedSkeleton,
		val renderer: SkeletonRenderer = SkeletonMeshRenderer
) {

	val skeleton: Skeleton
	val animationState: AnimationState

	var isPaused: Boolean = false

	private val quadTriangles = shortArrayOf(0, 1, 2, 2, 3, 0)

	init {
		skeleton = Skeleton(loadedSkeleton.skeletonData, loadedSkeleton.textureAtlasData)
		animationState = AnimationState(skeleton, AnimationStateData(loadedSkeleton.skeletonData))
	}

	fun activate() {
		for (loadedSkin in loadedSkeleton.loadedSkins.values) {
			for (texture in loadedSkin.pageTextures.values) {
				texture.refInc()
			}
		}
	}

	fun deactivate() {
		for (loadedSkin in loadedSkeleton.loadedSkins.values) {
			for (texture in loadedSkin.pageTextures.values) {
				texture.refDec()
			}
		}
	}

	fun tick(tickTime: Float) {
		if (isPaused || tickTime <= 0f) return
		animationState.update(tickTime)
		animationState.apply(skeleton)
		skeleton.updateWorldTransform()
	}

	fun draw(glState: GlState, concatenatedColorTint: ColorRo) {
		renderer.draw(loadedSkeleton, skeleton, glState, concatenatedColorTint)
	}

	fun getSlotAtPosition(x: Float, y: Float): Slot? = getSlotAtPosition(x, y, skeleton)

	private fun getSlotAtPosition(x: Float, y: Float, skeleton: Skeleton): Slot? {
		val drawOrder = skeleton.drawOrder
		for (i in drawOrder.lastIndex downTo 0) {
			val slot = drawOrder[i]
			val attachment = slot.attachment
			if (attachment is RegionAttachment) {
				val vertices = attachment.updateWorldVertices(slot)
				val triangles = quadTriangles
				if (intersects(x, y, vertices, triangles)) return slot
			} else if (attachment is MeshAttachment) {
				val vertices = attachment.updateWorldVertices(slot)
				val triangles = attachment.data.triangles
				if (intersects(x, y, vertices, triangles)) return slot
			} else if (attachment is WeightedMeshAttachment) {
				val vertices = attachment.updateWorldVertices(slot)
				val triangles = attachment.data.triangles
				if (intersects(x, y, vertices, triangles)) return slot
			} else if (attachment is SkeletonAttachment) {
				val attachmentSkeleton = attachment.skeleton ?: continue
				val bone = slot.bone
				val rootBone = attachmentSkeleton.rootBone!!
				val oldScaleX = rootBone.scaleX
				val oldScaleY = rootBone.scaleY
				val oldRotation = rootBone.rotation
				attachmentSkeleton.setPosition(skeleton.x + bone.worldX, skeleton.y + bone.worldY)
				rootBone.scaleX = (1f + bone.worldScaleX - oldScaleX)
				rootBone.scaleY = (1f + bone.worldScaleY - oldScaleY)
				rootBone.rotation = oldRotation + bone.worldRotationX
				attachmentSkeleton.updateWorldTransform()

				val attachedSkeletonSlot = getSlotAtPosition(x, y, attachmentSkeleton)

				attachmentSkeleton.setPosition(0f, 0f)
				rootBone.scaleX = oldScaleX
				rootBone.scaleY = oldScaleY
				rootBone.rotation = oldRotation

				if (attachedSkeletonSlot != null)
					return attachedSkeletonSlot
			}
		}
		return null
	}

	private val bounds = MinMax()
	private val pt = Vector2()
	private val v1 = Vector2()
	private val v2 = Vector2()
	private val v3 = Vector2()

	private fun intersects(x: Float, y: Float, vertices: List<Float>, triangles: ShortArray): Boolean {
		bounds.inf()
		pt.set(x, y)

		run {
			var i = positionOffset
			val n = vertices.size
			val vertexSize = spineVertexAttributes.vertexSize
			while (i < n) {
				bounds.ext(vertices[i], vertices[i + 1])
				i += vertexSize
			}
		}
		if (!bounds.contains(x, y))
			return false

		for (i in 0..triangles.lastIndex step 3) {
			SpineVertexUtils.getPosition(vertices, triangles[i], v1)
			SpineVertexUtils.getPosition(vertices, triangles[i + 1], v2)
			SpineVertexUtils.getPosition(vertices, triangles[i + 2], v3)
			if (GeomUtils.intersectPointTriangle(pt, v1, v2, v3)) {
				return true
			}
		}
		return false
	}
}

val spineVertexAttributes: VertexAttributes = VertexAttributes(listOf(
		VertexAttribute(2, false, Gl20.FLOAT, VertexAttributeUsage.POSITION),
		VertexAttribute(4, false, Gl20.FLOAT, VertexAttributeUsage.COLOR_TINT),
		VertexAttribute(2, false, Gl20.FLOAT, VertexAttributeUsage.TEXTURE_COORD))
)

object SpineVertexUtils {

	val positionOffset = spineVertexAttributes.getOffsetByUsage(VertexAttributeUsage.POSITION)!!
	val colorOffset = spineVertexAttributes.getOffsetByUsage(VertexAttributeUsage.COLOR_TINT)!!
	val textureCoordOffset = spineVertexAttributes.getOffsetByUsage(VertexAttributeUsage.TEXTURE_COORD)!!
	val vertexSize = spineVertexAttributes.vertexSize

	fun getPosition(vertexComponents: List<Float>, vertexIndex: Short, out: Vector2) {
		val p = vertexIndex * vertexSize + positionOffset
		out.set(vertexComponents[p], vertexComponents[p + 1])
	}

	fun getColor(vertexComponents: List<Float>, vertexIndex: Short, out: Color) {
		val p = vertexIndex * vertexSize + colorOffset
		out.set(vertexComponents[p], vertexComponents[p + 1], vertexComponents[p + 2], vertexComponents[p + 3])
	}

	fun getUPos(index: Int): Int {
		return index * vertexSize + textureCoordOffset
	}

	fun getVPos(index: Int): Int {
		return index * vertexSize + textureCoordOffset + 1
	}

	fun getXPos(index: Int): Int {
		return index * vertexSize + positionOffset
	}

	fun getYPos(index: Int): Int {
		return index * vertexSize + positionOffset + 1
	}


}

fun skeletonComponent(loadedSkeleton: LoadedSkeleton, init: ComponentInit<SkeletonComponent> = {}): SkeletonComponent {
	val s = SkeletonComponent(loadedSkeleton)
	s.init()
	return s
}
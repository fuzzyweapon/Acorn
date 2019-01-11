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

package com.acornui.component

import com.acornui.component.layout.algorithm.ScaleBoxLayoutContainer
import com.acornui.component.layout.algorithm.ScaleLayoutData
import com.acornui.core.di.Owned
import com.acornui.core.graphic.Texture

/**
 * A scale box layout
 */
open class Image(owner: Owned) : ScaleBoxLayoutContainer(owner) {

	override fun onElementAdded(oldIndex: Int, newIndex: Int, element: UiComponent) {
		super.onElementAdded(oldIndex, newIndex, element)
		if (element.layoutData !is ScaleLayoutData) {
			val layoutData = ScaleLayoutData()
			layoutData.maxScaleX = 1f
			layoutData.maxScaleY = 1f
			element.layoutData = layoutData
		}
	}

}

fun Owned.image(init: ComponentInit<Image> = {}): Image {
	val i = Image(this)
	i.init()
	return i
}

fun Owned.image(path: String, init: ComponentInit<Image> = {}): Image {
	val i = Image(this)
	i.init()
	i.contentsImage(path)
	return i
}

fun Owned.image(atlasPath: String, region: String, init: ComponentInit<Image> = {}): Image {
	val i = Image(this)
	i.init()
	i.contentsAtlas(atlasPath, region)
	return i
}

/**
 * Creates a texture component and uses it as the element of a single element container.
 */
fun SingleElementContainer<UiComponent>.contentsImage(value: String) {
	createOrReuseElement { textureC() }.path = value
}

/**
 * Creates a texture component and uses it as the element of a single element container.
 */
fun SingleElementContainer<UiComponent>.contentsTexture(value: Texture?) {
	createOrReuseElement { textureC() }.texture = value
}
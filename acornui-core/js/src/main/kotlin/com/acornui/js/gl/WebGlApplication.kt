/*
 * Copyright 2014 Nicholas Bilyk
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

package com.acornui.js.gl

import com.acornui.async.launch
import com.acornui.component.GlStageImpl
import com.acornui.component.HtmlComponent
import com.acornui.component.Stage
import com.acornui.core.AppConfig
import com.acornui.core.asset.AssetType
import com.acornui.core.asset.LoaderFactory
import com.acornui.core.di.Owned
import com.acornui.core.di.dKey
import com.acornui.core.di.own
import com.acornui.core.focus.FakeFocusMouse
import com.acornui.core.focus.FocusManager
import com.acornui.core.focus.FocusManagerImpl
import com.acornui.core.graphic.Window
import com.acornui.gl.core.Gl20
import com.acornui.gl.core.GlState
import com.acornui.gl.core.GlStateImpl
import com.acornui.js.JsApplicationBase
import com.acornui.js.html.JsHtmlComponent
import com.acornui.js.html.WebGl
import com.acornui.js.input.JsClickDispatcher
import org.khronos.webgl.WebGLContextAttributes
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.browser.document
import kotlin.dom.clear

/**
 * @author nbilyk
 */
@Suppress("unused")
open class WebGlApplication(private val rootId: String) : JsApplicationBase() {

	override val canvasTask by BootTask {
		val rootElement = document.getElementById(rootId) ?: throw Exception("Could not find root canvas $rootId")
		val root = rootElement as HTMLElement
		root.style.setProperty("-webkit-tap-highlight-color", "rgba(0,0,0,0)")
		root.clear()
		val canvas = document.createElement("canvas") as HTMLCanvasElement
		canvas.style.setProperty("-webkit-tap-highlight-color", "rgba(0,0,0,0)")
		canvas.style.apply {
			width = "100%"
			height = "100%"
			position = "absolute"
		}
		root.appendChild(canvas)
		set(CANVAS, canvas)
	}

	protected open val glTask by BootTask {
		val config = get(AppConfig)
		val glConfig = config.gl
		val attributes = WebGLContextAttributes()
		attributes.alpha = glConfig.alpha
		attributes.antialias = glConfig.antialias
		attributes.depth = glConfig.depth
		attributes.stencil = glConfig.stencil
		attributes.premultipliedAlpha = false

		val context = WebGl.getContext(get(CANVAS), attributes) ?: throw Exception("Browser does not support WebGL") // TODO: Make this a better UX
		val gl = WebGl20(context)
		set(Gl20, gl)
	}
	
	override val windowTask by BootTask {
		set(Window, WebGlWindowImpl(get(CANVAS), config().window, get(Gl20)))
	}

	protected open val glStateTask by BootTask {
		set(GlState, GlStateImpl(get(Gl20), get(Window)))
	}

	protected open val textureLoaderTask by BootTask {
	}

	override fun addAssetLoaders(loaders: MutableMap<AssetType<*>, LoaderFactory<*>>) {
		super.addAssetLoaders(loaders)
		launch {
			val gl = get(Gl20)
			val glState = get(GlState)
			loaders[AssetType.TEXTURE] = { path, estimatedBytesTotal -> WebGlTextureLoader(path, estimatedBytesTotal, gl, glState) }
		}
	}

	/**
	 * The last chance to set dependencies on the application scope.
	 */
	override val componentsTask  by BootTask {
		val root = document.getElementById(rootId) as HTMLElement
		set(HtmlComponent.FACTORY_KEY, { JsHtmlComponent(it, root) })
	}

	override val focusManagerTask by BootTask {
		// When the focused element changes, make sure the document's active element is the canvas.
		val canvas = get(CANVAS)
		val focusManager = FocusManagerImpl()
		focusManager.focusedChanged.add {
			old, new ->
			if (new != null) {
				canvas.focus()
			}
		}
		set(FocusManager, focusManager)
	}

	override suspend fun createStage(owner: Owned): Stage {
		return GlStageImpl(owner)
	}

	override suspend fun initializeSpecialInteractivity(owner: Owned) {
		super.initializeSpecialInteractivity(owner)
		owner.own(FakeFocusMouse(owner.injector))
		owner.own(JsClickDispatcher(get(CANVAS), owner.injector))
	}
	
	companion object {
		val CANVAS = dKey<HTMLCanvasElement, HTMLElement>(JsApplicationBase.CANVAS)
	}

}
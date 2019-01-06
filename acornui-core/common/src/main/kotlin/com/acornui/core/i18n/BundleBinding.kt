/*
 * Copyright 2017 Nicholas Bilyk
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

package com.acornui.core.i18n

import com.acornui.core.Disposable
import com.acornui.core.di.Injector
import com.acornui.core.di.Scoped
import com.acornui.core.di.inject
import com.acornui.observe.Observable
import com.acornui.signal.Signal
import com.acornui.signal.Signal1

/**
 * This class is responsible for tracking a set of callbacks and cached files for an [I18n] bundle.
 * When this binding is disposed, the handlers are all removed and the cached file references are decremented.
 */
class BundleBinding(override val injector: Injector, bundleName: String) : Scoped, Disposable, I18nBundleRo {

	private val _changed = Signal1<I18nBundleRo>()
	override val changed = _changed.asRo()


	/**
	 * The bundle this binding is watching.
	 */
	private val bundle: I18nBundleRo = inject(I18n).getBundle(bundleName)

	private var bundleLoader = loadBundle(bundleName)

	init {
		bundle.changed.add(this::bundleChangedHandler)
	}

	fun bind(callback: (bundle: I18nBundleRo) -> Unit): Disposable {
		_changed.add(callback)
		callback(bundle)
		return object : Disposable {
			override fun dispose() = _changed.remove(callback)
		}
	}

	private fun bundleChangedHandler(o: Observable) {
		_changed.dispatch(this)
	}

	override fun get(key: String): String? = bundle[key]

	override fun dispose() {
		bundle.changed.remove(this::bundleChangedHandler)
		bundleLoader.dispose()
		_changed.dispose()
	}
}

/**
 * Instantiates a bundle binding object.
 */
fun Scoped.bundleBinding(bundleName: String): BundleBinding {
	return BundleBinding(injector, bundleName)
}

/**
 * Invokes the callback when this bundle has changed.
 */
fun Scoped.i18n(bundleName: String) : BundleBinding {
	return BundleBinding(injector, bundleName)
}
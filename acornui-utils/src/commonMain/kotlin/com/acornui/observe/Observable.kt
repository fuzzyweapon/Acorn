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

package com.acornui.observe

import com.acornui.signal.Bindable
import com.acornui.signal.Signal

interface Observable : Bindable {

	/**
	 * Dispatched when this object has changed.
	 */
	val changed: Signal<(Observable) -> Unit>

	override fun addBinding(callback: () -> Unit) {
		changed.addBinding(callback)
	}

	override fun removeBinding(callback: () -> Unit) {
		changed.removeBinding(callback)
	}
}
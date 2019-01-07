package com.acornui.core.time

import com.acornui.component.UiComponentRo
import com.acornui.core.Disposable
import com.acornui.core.LifecycleRo
import com.acornui.core.UpdatableChildBase
import com.acornui.core.di.inject

private class OnTick(
		private val component: UiComponentRo,
		private val callback: (tickTime: Float) -> Unit
) : UpdatableChildBase(), Disposable {

	private val timeDriver = component.inject(TimeDriver)

	private val componentActivatedHandler: (LifecycleRo) -> Unit = {
		timeDriver.addChild(this)
	}

	private val componentDeactivatedHandler: (LifecycleRo) -> Unit = {
		timeDriver.removeChild(this)
	}

	private val componentDisposedHandler: (LifecycleRo) -> Unit = {
		dispose()
	}

	init {
		component.activated.add(componentActivatedHandler)
		component.deactivated.add(componentDeactivatedHandler)
		component.disposed.add(componentDisposedHandler)

		if (component.isActive) {
			timeDriver.addChild(this)
		}
	}

	override fun update(tickTime: Float) {
		callback(tickTime)
	}

	override fun dispose() {
		remove()
		component.activated.remove(componentActivatedHandler)
		component.deactivated.remove(componentDeactivatedHandler)
		component.disposed.remove(componentDisposedHandler)
	}
}

/**
 * While the receiver component is activated, every time driver tick will invoke the callback.
 *
 * @return An instance that can be disposed to stop watching ticks.
 */
fun UiComponentRo.onTick(callback: (tickTime: Float) -> Unit): Disposable {
	return OnTick(this, callback)
}
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

package com.acornui.core.time

import com.acornui._assert
import com.acornui.collection.ActiveList
import com.acornui.collection.iterate
import com.acornui.core.*
import com.acornui.core.di.DKey
import com.acornui.core.di.Scoped
import com.acornui.core.di.inject

/**
 * The time driver is responsible for invoking [UpdatableChild.update] on anything that changes over time, like
 * animations, physics, etc.
 *
 * @author nbilyk
 */
interface TimeDriver : Parent<UpdatableChild> {

	/**
	 * The configuration provided to this time driver.
	 */
	val config: TimeDriverConfig

	fun activate()

	fun update()

	companion object : DKey<TimeDriver>
}

/**
 * @author nbilyk
 */
open class TimeDriverImpl(

		final override val config: TimeDriverConfig
) : TimeDriver, Disposable {

	override var parent: Parent<out ChildRo>? = null
	private val _children = ActiveList<UpdatableChild>()
	private val childIterator = _children.concurrentIterator()

	/**
	 * The next time to do a step.
	 */
	private var nextTick: Double = 0.0

	private val tickTime: Float = config.tickTime
	private val maxTicksPerUpdate: Int = config.maxTicksPerUpdate

	override fun activate() {
		nextTick = time.nowS()
	}

	override fun update() {
		var loops = 0
		val now = time.nowS()
		while (now > nextTick) {
			nextTick += tickTime
			tick(tickTime)
			if (++loops > maxTicksPerUpdate) {
				// If we're too far behind, break and reset.
				nextTick = time.nowS() + tickTime
				break
			}
		}
	}

	protected open fun tick(tickTime: Float) {
		childIterator.iterate {
			it.update(tickTime)
			true
		}
	}

	//-----------------------------------------------
	// Parent
	//-----------------------------------------------

	override val children: List<UpdatableChild>
		get() = _children

	/**
	 * Adds the specified child to this container.
	 * @param index The index of where to insert the child. By default this is the end of the list.
	 */
	override fun <S : UpdatableChild> addChild(index: Int, child: S): S {
		val n = _children.size
		_assert(index <= n, "index is out of bounds.")
		_assert(child.parent == null, "Remove the child before adding it again.")
		if (index == n) {
			_children.add(child)
		} else {
			_children.add(index, child)
		}
		child.parent = this
		return child
	}

	/**
	 * Removes a child at the given index from this container.
	 * @return Returns true if a child was removed, or false if the index was out of range.
	 */
	override fun removeChild(index: Int): UpdatableChild {
		val c = _children
		val child = c.removeAt(index)
		child.parent = null
		return child
	}

	override fun dispose() {
		_children.clear()
	}
}

/**
 * Invokes the callback on every frame. This is similar to [onTick] except the receiver isn't watched for activation
 * or disposal.
 */
fun Scoped.drive(update: (tickTime: Float) -> Unit): UpdatableChild {
	val child = object : UpdatableChildBase() {
		override fun update(tickTime: Float) {
			update(tickTime)
		}
	}
	inject(TimeDriver).addChild(child)
	return child
}
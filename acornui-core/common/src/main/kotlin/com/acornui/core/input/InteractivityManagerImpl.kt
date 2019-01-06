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

package com.acornui.core.input

import com.acornui._assert
import com.acornui.collection.ClearableObjectPool
import com.acornui.collection.arrayListObtain
import com.acornui.collection.arrayListPool
import com.acornui.component.StageRo
import com.acornui.component.UiComponentRo
import com.acornui.component.ancestry
import com.acornui.component.getChildUnderPoint
import com.acornui.core.focus.FocusManager
import com.acornui.core.input.interaction.*
import com.acornui.core.time.time
import com.acornui.signal.StoppableSignalImpl


// TODO: possibly add a re-validation when the HIERARCHY has been invalidated. (use case: when an element has moved out from underneath the mouse)
/**
 * An implementation of InteractivityManager
 *
 * Must set the root ui component to use when propagating input.
 * @author nbilyk
 */
open class InteractivityManagerImpl(
		private val mouseInput: MouseInput,
		private val keyInput: KeyInput,
		private val focus: FocusManager) : InteractivityManager {

	private var _root: UiComponentRo? = null
	private val root: UiComponentRo
		get() = _root!!

	private val mousePool = ClearableObjectPool { MouseInteraction() }
	private val touchPool = ClearableObjectPool { TouchInteraction() }
	private val wheelPool = ClearableObjectPool { WheelInteraction() }
	private val keyPool = ClearableObjectPool { KeyInteraction() }
	private val charPool = ClearableObjectPool { CharInteraction() }

	private val overTargets = ArrayList<UiComponentRo>()


	private fun overCanvasChangedHandler(overCanvas: Boolean) {
		if (!overCanvas)
			overTarget(null)
	}

	private fun rawTouchStartHandler(event: TouchInteraction) {
		touchHandler(TouchInteractionRo.TOUCH_START, event)
	}

	private fun rawTouchEndHandler(event: TouchInteraction) {
		touchHandler(TouchInteractionRo.TOUCH_END, event)
	}

	private fun rawTouchMoveHandler(event: TouchInteraction) {
		overTarget(touchHandler(TouchInteractionRo.TOUCH_MOVE, event))
	}

	private fun rawMouseDownHandler(event: MouseInteraction) {
		mouseHandler(MouseInteractionRo.MOUSE_DOWN, event)
	}

	private fun rawMouseUpHandler(event: MouseInteraction) {
		mouseHandler(MouseInteractionRo.MOUSE_UP, event)
	}

	private fun rawMouseMoveHandler(event: MouseInteraction) {
		overTarget(mouseHandler(MouseInteractionRo.MOUSE_MOVE, event))
	}

	private fun rawWheelHandler(event: WheelInteraction) {
		val wheel = wheelPool.obtain()
		wheel.set(event)
		wheel.type = WheelInteractionRo.MOUSE_WHEEL
		dispatch(event.canvasX + 0.5f, event.canvasY + 0.5f, wheel)
		if (wheel.defaultPrevented())
			event.preventDefault()
		wheelPool.free(wheel)
	}

	private fun <T : MouseInteractionRo> mouseHandler(type: InteractionType<T>, event: MouseInteraction): UiComponentRo? {
		val mouse = mousePool.obtain()
		mouse.set(event)
		mouse.type = type
		val ele = root.getChildUnderPoint(mouse.canvasX + 0.5f, mouse.canvasY + 0.5f, onlyInteractive = true) ?: root
		dispatch(ele, mouse, true, true)
		if (mouse.defaultPrevented())
			event.preventDefault()
		mousePool.free(mouse)
		return ele
	}

	private fun touchHandler(type: InteractionType<TouchInteractionRo>, event: TouchInteraction): UiComponentRo? {
		val touch = touchPool.obtain()
		touch.set(event)
		touch.type = type
		val first = event.changedTouches.first()
		val ele = root.getChildUnderPoint(first.canvasX + 0.5f, first.canvasY + 0.5f, onlyInteractive = true) ?: root
		dispatch(ele, touch, true, true)
		if (touch.defaultPrevented())
			event.preventDefault()
		touchPool.free(touch)
		return ele
	}

	private fun keyDownHandler(event: KeyInteractionRo) {
		keyHandler(KeyInteractionRo.KEY_DOWN, event)
	}

	private fun keyUpHandler(event: KeyInteractionRo) {
		keyHandler(KeyInteractionRo.KEY_UP, event)
	}

	private fun charHandler(event: CharInteractionRo) {
		charHandler(CharInteractionRo.CHAR, event)
	}

	private fun <T : KeyInteractionRo> keyHandler(type: InteractionType<T>, event: KeyInteractionRo) {
		val f = focus.focused ?: return
		val key = keyPool.obtain()
		key.type = type
		key.set(event)
		dispatch(f, key)
		if (key.defaultPrevented()) event.preventDefault()
		keyPool.free(key)
	}

	private fun <T : CharInteractionRo> charHandler(type: InteractionType<T>, event: CharInteractionRo) {
		val f = focus.focused ?: return
		val char = charPool.obtain()
		char.type = CharInteractionRo.CHAR
		char.set(event)
		dispatch(f, char)
		if (char.defaultPrevented())
			event.preventDefault()
		charPool.free(char)
	}

	override fun init(root: StageRo) {
		_assert(_root == null, "Already initialized.")
		_root = root
		mouseInput.overCanvasChanged.add(this::overCanvasChangedHandler)
		mouseInput.mouseDown.add(this::rawMouseDownHandler)
		mouseInput.mouseUp.add(this::rawMouseUpHandler)
		mouseInput.mouseMove.add(this::rawMouseMoveHandler)
		mouseInput.mouseWheel.add(this::rawWheelHandler)

		mouseInput.touchStart.add(this::rawTouchStartHandler)
		mouseInput.touchEnd.add(this::rawTouchEndHandler)
		mouseInput.touchMove.add(this::rawTouchMoveHandler)

		keyInput.keyDown.add(this::keyDownHandler)
		keyInput.keyUp.add(this::keyUpHandler)
		keyInput.char.add(this::charHandler)
	}

	private fun overTarget(target: UiComponentRo?) {
		val previousOverTarget = overTargets.firstOrNull()
		if (target == previousOverTarget) return
		val mouse = mousePool.obtain()
		mouse.canvasX = mouseInput.canvasX
		mouse.canvasY = mouseInput.canvasY
		mouse.button = WhichButton.UNKNOWN
		mouse.timestamp = time.nowMs()

		if (previousOverTarget != null) {
			mouse.relatedTarget = target
			mouse.target = previousOverTarget
			mouse.type = MouseInteractionRo.MOUSE_OUT
			dispatch(overTargets, mouse)
		}
		if (target != null) {
			target.ancestry(overTargets)
			mouse.relatedTarget = previousOverTarget
			mouse.target = target
			mouse.type = MouseInteractionRo.MOUSE_OVER
			dispatch(overTargets, mouse)
		} else {
			overTargets.clear()
		}
		mousePool.free(mouse)
	}

	override fun <T : InteractionEventRo> getSignal(host: UiComponentRo, type: InteractionType<T>, isCapture: Boolean): StoppableSignalImpl<T> {
		return StoppableSignalImpl()
	}

	/**
	 * Dispatches an interaction for the layout element at the given stage position.
	 * @param canvasX The x coordinate relative to the canvas.
	 * @param canvasY The y coordinate relative to the canvas.
	 * @param event The interaction event to dispatch.
	 */
	override fun dispatch(canvasX: Float, canvasY: Float, event: InteractionEvent, useCapture: Boolean, useBubble: Boolean) {
		val ele = root.getChildUnderPoint(canvasX, canvasY, onlyInteractive = true) ?: root
		dispatch(ele, event, useCapture, useBubble)
	}

	/**
	 * Dispatches an interaction for a single interactive element.
	 * This will first dispatch a capture event from the stage down to the given target, and then
	 * a bubbling event up to the stage.
	 */
	override fun dispatch(target: UiComponentRo, event: InteractionEvent, useCapture: Boolean, useBubble: Boolean) {
		event.target = target
		if (!useCapture && !useBubble) {
			// Dispatch only for current target.
			dispatchForCurrentTarget(target, event, isCapture = false)
		} else {
			val rawAncestry = arrayListObtain<UiComponentRo>()
			target.ancestry(rawAncestry)
			dispatch(rawAncestry, event, useCapture, useBubble)
			arrayListPool.free(rawAncestry)
		}
	}

	private fun dispatch(rawAncestry: List<UiComponentRo>, event: InteractionEvent, useCapture: Boolean = true, useBubble: Boolean = true) {
		// Capture phase
		if (useCapture) {
			for (i in rawAncestry.lastIndex downTo 0) {
				if (event.propagation.propagationStopped()) break
				dispatchForCurrentTarget(rawAncestry[i], event, isCapture = true)
			}
		}
		// Bubble phase
		if (useBubble) {
			for (i in 0..rawAncestry.lastIndex) {
				if (event.propagation.propagationStopped()) break
				dispatchForCurrentTarget(rawAncestry[i], event, isCapture = false)
			}
		}
	}

	private fun dispatchForCurrentTarget(currentTarget: UiComponentRo, event: InteractionEvent, isCapture: Boolean) {
		val signal = currentTarget.getInteractionSignal(event.type, isCapture) as StoppableSignalImpl?
		if (signal != null && signal.isNotEmpty()) {
			event.localize(currentTarget)
			signal.dispatch(event)
		}
	}

	override fun dispose() {
		overTarget(null)

		val mouse = mouseInput
		mouse.mouseDown.remove(this::rawMouseDownHandler)
		mouse.mouseUp.remove(this::rawMouseUpHandler)
		mouse.mouseMove.remove(this::rawMouseMoveHandler)
		mouse.mouseWheel.remove(this::rawWheelHandler)

		mouseInput.touchStart.remove(this::rawTouchStartHandler)
		mouseInput.touchEnd.remove(this::rawTouchEndHandler)
		mouseInput.touchMove.remove(this::rawTouchMoveHandler)

		val key = keyInput
		key.keyDown.remove(this::keyDownHandler)
		key.keyUp.remove(this::keyUpHandler)
		key.char.remove(this::charHandler)
	}
}
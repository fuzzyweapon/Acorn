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

@file:Suppress("UNUSED_PARAMETER")

package com.acornui.component.text

import com.acornui.collection.Clearable
import com.acornui.component.*
import com.acornui.component.scroll.ClampedScrollModel
import com.acornui.component.scroll.ScrollPolicy
import com.acornui.component.scroll.scrollArea
import com.acornui.component.scroll.scrollTo
import com.acornui.component.style.StyleTag
import com.acornui.component.style.Styleable
import com.acornui.component.style.set
import com.acornui.core.Disposable
import com.acornui.core.config
import com.acornui.core.di.Owned
import com.acornui.core.di.inject
import com.acornui.core.focus.Focusable
import com.acornui.core.input.*
import com.acornui.core.input.interaction.*
import com.acornui.core.mvc.CommandGroup
import com.acornui.core.mvc.invokeCommand
import com.acornui.core.repeat2
import com.acornui.core.selection.*
import com.acornui.core.tickTime
import com.acornui.core.time.tick
import com.acornui.math.*
import com.acornui.signal.Signal

interface TextInput : Focusable, SelectableComponent, Styleable, Clearable {

	val charStyle: CharStyle
	val flowStyle: TextFlowStyle
	val boxStyle: BoxStyle
	val textInputStyle: TextInputStyle

	/**
	 * Dispatched on each input character.
	 * Note - this does not invoke when the text is programmatically changed.
	 */
	val input: Signal<() -> Unit>

	/**
	 * Dispatched on value commit.
	 * This is only dispatched on a user interaction, such as pressing ENTER or TAB. It is not dispatched when
	 * the text is programmatically changed.
	 */
	val changed: Signal<() -> Unit>

	var editable: Boolean
	var maxLength: Int?

	var text: String

	var placeholder: String

	/**
	 * A regular expression pattern to define what is NOT allowed in this text input.
	 * E.g. "[a-z]" will prevent lowercase letters from being entered.
	 * Setting this will mutate the current [text] property.
	 *
	 * Note: In the future, this will be changed to restrict: Regex, currently KT-17851 prevents this.
	 * Note: The global flag will be used.
	 */
	var restrictPattern: String?

	var password: Boolean

	/**
	 * If true, pressing TAB inserts a tab character as opposed to the default behavior (typically a focus change).
	 */
	var allowTab: Boolean

	// TODO: add prompt

	companion object : StyleTag
}

// TODO: Reduce this. instead of boxStyle, use a background component
// TODO: instead of cursor component, just use 4 vertices

class TextInputImpl(owner: Owned) : ContainerImpl(owner), TextInput {

	private val background = addChild(rect())

	override val textInputStyle = bind(TextInputStyle())
	override val boxStyle = bind(BoxStyle())
	private val editableText = addChild(EditableText(this))

	override val charStyle: CharStyle
		get() = editableText.charStyle

	override val flowStyle: TextFlowStyle
		get() = editableText.flowStyle

	override val input: Signal<() -> Unit>
		get() = editableText.input

	override val changed: Signal<() -> Unit>
		get() = editableText.changed

	override var editable: Boolean
		get() = editableText.editable
		set(value) {
			editableText.editable = value
		}

	override var maxLength: Int?
		get() = editableText.maxLength
		set(value) {
			editableText.maxLength = value
		}

	override var text: String
		get() = editableText.text
		set(value) {
			editableText.text = value
		}

	override var placeholder: String
		get() = editableText.placeholder
		set(value) {
			editableText.placeholder = value
		}

	override var restrictPattern: String?
		get() = editableText.restrictPattern
		set(value) {
			editableText.restrictPattern = value
		}

	override var password: Boolean
		get() = editableText.password
		set(value) {
			editableText.password = value
		}

	override var allowTab: Boolean
		get() = editableText.allowTab
		set(value) {
			editableText.allowTab = value
		}

	init {
		focusEnabled = true
		styleTags.add(TextInput)
		watch(boxStyle) {
			background.style.set(it)
		}
		watch(textInputStyle) {
			editableText.cursorColorOne = it.cursorColorOne
			editableText.cursorColorTwo = it.cursorColorTwo
			editableText.cursorBlinkSpeed = it.cursorBlinkSpeed
			invalidateLayout()
		}
	}

	private var defaultWidthFromText: String? = null

	/**
	 * Sets this text input's default width to fit the character 'M' repeated [textLength] times.
	 * If this text input has been given either an explicit width, or a [defaultWidth], this will have no effect.
	 */
	fun setSizeToFit(textLength: Int) = setSizeToFit("M".repeat2(textLength))

	/**
	 * Sets this text input's default width to fit the given text line.
	 * If this text input has been given either an explicit width, or a [defaultWidth], this will have no effect.
	 */
	fun setSizeToFit(text: String?) {
		defaultWidthFromText = text
		invalidateLayout()
	}

	override fun updateStyles() {
		super.updateStyles()
		// This class's styles are delegated from the editableText styles.
		editableText.validate(ValidationFlags.STYLES)
	}

	override fun updateLayout(explicitWidth: Float?, explicitHeight: Float?, out: Bounds) {
		val pad = boxStyle.padding
		val margin = boxStyle.margin
		val h = margin.reduceHeight(pad.reduceHeight(explicitHeight))

		val w = if (explicitWidth == null && defaultWidthFromText != null) {
			val font = charStyle.font
			font?.data?.measureLineWidth(defaultWidthFromText!!)?.toFloat() ?: 0f
		} else {
			margin.reduceWidth2(pad.reduceWidth2(explicitWidth ?: textInputStyle.defaultWidth))
		}

		editableText.setSize(w, h)
		editableText.setPosition(margin.left + pad.left, margin.top + pad.top)
		out.set(explicitWidth ?: margin.expandHeight2(pad.expandHeight2(w)), explicitHeight
				?: margin.expandHeight2(pad.expandHeight2(editableText.height)))
		background.setSize(margin.reduceWidth2(out.width), margin.reduceHeight(out.height))
		background.setPosition(margin.left, margin.top)
	}

	override fun clear() {
		text = ""
	}
}


interface TextArea : TextInput {

	val hScrollModel: ClampedScrollModel
	val vScrollModel: ClampedScrollModel

	/**
	 * The horizontal scrolling policy.
	 * Default: ScrollPolicy.OFF
	 */
	var hScrollPolicy: ScrollPolicy

	/**
	 * The vertical scrolling policy.
	 * Default: ScrollPolicy.AUTO
	 */
	var vScrollPolicy: ScrollPolicy

	val contentsWidth: Float
	val contentsHeight: Float

	companion object : StyleTag
}

class TextAreaImpl(owner: Owned) : ContainerImpl(owner), TextArea {

	private val background = addChild(rect())

	override val textInputStyle = bind(TextInputStyle())
	override val boxStyle = bind(BoxStyle())

	private val editableText = EditableText(this).apply {
		textField.allowClipping = false
	}

	private val scroller = addChild(scrollArea {
		hScrollPolicy = ScrollPolicy.OFF
		+editableText layout { widthPercent = 1f }
	})

	override val charStyle: CharStyle
		get() = editableText.charStyle

	override val flowStyle: TextFlowStyle
		get() = editableText.flowStyle

	override val input: Signal<() -> Unit>
		get() = editableText.input

	override val changed: Signal<() -> Unit>
		get() = editableText.changed

	override var editable: Boolean
		get() = editableText.editable
		set(value) {
			editableText.editable = value
		}

	override var maxLength: Int?
		get() = editableText.maxLength
		set(value) {
			editableText.maxLength = value
		}

	override var text: String
		get() = editableText.text
		set(value) {
			editableText.text = value
		}

	override var placeholder: String
		get() = editableText.placeholder
		set(value) {
			editableText.placeholder = value
		}

	override var restrictPattern: String?
		get() = editableText.restrictPattern
		set(value) {
			editableText.restrictPattern = value
		}

	override var password: Boolean
		get() = editableText.password
		set(value) {
			editableText.password = value
		}

	override var allowTab: Boolean
		get() = editableText.allowTab
		set(value) {
			editableText.allowTab = value
		}

	override val hScrollModel: ClampedScrollModel
		get() = scroller.hScrollModel

	override val vScrollModel: ClampedScrollModel
		get() = scroller.vScrollModel

	override var hScrollPolicy: ScrollPolicy
		get() = scroller.hScrollPolicy
		set(value) {
			scroller.hScrollPolicy = value
		}

	override var vScrollPolicy: ScrollPolicy
		get() = scroller.vScrollPolicy
		set(value) {
			scroller.vScrollPolicy = value
		}

	override val contentsWidth: Float
		get() = scroller.contentsWidth

	override val contentsHeight: Float
		get() = scroller.contentsHeight

	private val selectionManager = inject(SelectionManager)
	private val rect = Rectangle()

	init {
		focusEnabled = true
		styleTags.add(TextInput)
		styleTags.add(TextArea)
		watch(boxStyle) {
			scroller.stackStyle.padding = it.padding
			scroller.style.borderRadius = it.borderRadii
			background.style.set(it)
		}
		watch(textInputStyle) {
			editableText.cursorColorOne = it.cursorColorOne
			editableText.cursorColorTwo = it.cursorColorTwo
			editableText.cursorBlinkSpeed = it.cursorBlinkSpeed
			invalidateLayout()
		}

		mouseDown().add(this::startScrollWatch)
		touchStart().add(this::startScrollWatch)
		keyDown().add(this::scrollToSelected)
	}

	private fun scrollToSelected(event: KeyInteractionRo) {
		val sel = firstSelection ?: return
		val e = if (sel.endIndex >= contents.textElementsCount) contents.placeholder else contents.getTextElementAt(sel.endIndex)
		rect.set(e.x, e.y, e.width, e.lineHeight)
		rect.inflate(flowStyle.padding)

		scroller.scrollTo(rect)
	}

	private val contents
		get() = editableText.textField.contents

	private val maxScrollSpeed = 1000f * tickTime
	private val bufferP = 0.2f
	private val innerBufferMax = 80f
	private val outerBufferMax = 200f
	private var startMouse = Vector2()
	private val currentMouse = Vector2()
	private var _frameWatch: Disposable? = null

	private fun startScrollWatch(event: Any) {
		mousePosition(startMouse)
		_frameWatch?.dispose()
		_frameWatch = tick(-1, this::scrollWatcher)
		stage.mouseUp().add(this::endScrollWatch)
		stage.touchEnd().add(this::endScrollWatch)
	}

	private fun endScrollWatch(event: Any) {
		_frameWatch?.dispose()
		_frameWatch = null
		stage.mouseUp().remove(this::endScrollWatch)
		stage.touchEnd().remove(this::endScrollWatch)
	}

	private fun scrollWatcher() {
		mousePosition(currentMouse)
		if (hScrollPolicy != ScrollPolicy.OFF) {
			val width = width
			val b = maxOf(0f, minOf4(innerBufferMax, width * bufferP, startMouse.x, width - startMouse.x))
			val speed = if (currentMouse.x < b) {
				-(1f - (currentMouse.x + outerBufferMax) / (b + outerBufferMax))
			} else if (currentMouse.x > width - b) {
				(currentMouse.x - width + b) / (b + outerBufferMax)
			} else {
				0f
			}
			hScrollModel.value += speed * maxScrollSpeed
		}
		if (vScrollPolicy != ScrollPolicy.OFF) {
			val height = height
			val b = maxOf(0f, minOf4(innerBufferMax, height * bufferP, startMouse.y, height - startMouse.y))
			val speed = if (currentMouse.y < b) {
				-(1f - (currentMouse.y + outerBufferMax) / (b + outerBufferMax))
			} else if (currentMouse.y > height - b) {
				(currentMouse.y - height + b) / (b + outerBufferMax)
			} else {
				0f
			}
			vScrollModel.value += speed * maxScrollSpeed
		}
	}

	override fun updateLayout(explicitWidth: Float?, explicitHeight: Float?, out: Bounds) {
		val margin = boxStyle.margin
		val w = margin.reduceWidth2(explicitWidth ?: textInputStyle.defaultWidth)
		val h = margin.reduceHeight(explicitHeight)
		scroller.setSize(w, h)
		scroller.setPosition(margin.left, margin.top)
		editableText.pageHeight = h ?: 400f
		out.set(explicitWidth ?: textInputStyle.defaultWidth, explicitHeight ?: margin.expandHeight2(scroller.height))
		background.setSize(margin.reduceWidth2(out.width), margin.reduceHeight(out.height))
		background.setPosition(margin.left, margin.top)
	}

	override fun clear() {
		text = ""
	}

	private val firstSelection: SelectionRange?
		get() = selectionManager.selection.firstOrNull { it.target == this }

}


/**
 * Replaces the given range with the provided text.
 * This is functionally the same as:
 * text.substring(0, startIndex) + newText + text.substring(endIndex, text.length)
 *
 * @param startIndex The starting character index for the replacement. (Inclusive)
 * @param endIndex The ending character index for the replacement. (Exclusive)
 *
 * E.g.
 * +text("Hello World") {
 *   replaceTextRange(1, 5, "i") // Hi World
 * }
 */
fun TextInput.replaceTextRange(startIndex: Int, endIndex: Int, newText: String, group: CommandGroup? = null) {
	invokeCommand(ReplaceTextRangeCommand(this, startIndex, text.substring(MathUtils.clamp(startIndex, 0, text.length), MathUtils.clamp(endIndex, 0, text.length)), newText, group))
}

var TextInput.selectable: Boolean
	get(): Boolean = charStyle.selectable
	set(value) {
		charStyle.selectable = value
	}


var TextArea.selectable: Boolean
	get(): Boolean = charStyle.selectable
	set(value) {
		charStyle.selectable = value
	}


fun Owned.textInput(init: ComponentInit<TextInputImpl> = {}): TextInputImpl {
	val t = TextInputImpl(this)
	t.init()
	return t
}

fun Owned.textArea(init: ComponentInit<TextAreaImpl> = {}): TextAreaImpl {
	val t = TextAreaImpl(this)
	t.init()
	return t
}

/**
 * Common text restrict patterns.
 */
object RestrictPatterns {

	const val INTEGER = "[^0-9+-]"
	const val FLOAT = "[^0-9+-.]"
}
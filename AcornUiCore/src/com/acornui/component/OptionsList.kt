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

import com.acornui.collection.*
import com.acornui.component.layout.DataScrollerStyle
import com.acornui.component.layout.ListItemRenderer
import com.acornui.component.layout.ListRenderer
import com.acornui.component.layout.algorithm.LayoutDataProvider
import com.acornui.component.layout.algorithm.VerticalLayoutData
import com.acornui.component.layout.algorithm.virtual.ItemRendererOwner
import com.acornui.component.layout.algorithm.virtual.VirtualVerticalLayoutStyle
import com.acornui.component.layout.algorithm.virtual.vDataScroller
import com.acornui.component.scroll.ScrollModel
import com.acornui.component.style.*
import com.acornui.component.text.TextInput
import com.acornui.component.text.textInput
import com.acornui.core.Disposable
import com.acornui.core.di.Owned
import com.acornui.core.di.inject
import com.acornui.core.di.own
import com.acornui.core.di.owns
import com.acornui.core.focus.FocusContainer
import com.acornui.core.focus.FocusManager
import com.acornui.core.focus.Focusable
import com.acornui.core.focus.focusFirst
import com.acornui.core.input.Ascii
import com.acornui.core.input.interaction.KeyInteractionRo
import com.acornui.core.input.keyDown
import com.acornui.core.input.mouseDown
import com.acornui.core.popup.lift
import com.acornui.core.text.StringFormatter
import com.acornui.core.text.ToStringFormatter
import com.acornui.math.Bounds
import com.acornui.math.Pad
import com.acornui.observe.bind
import com.acornui.reflect.observable
import com.acornui.signal.Signal
import com.acornui.signal.Signal0

// TODO: delegate focus to input

open class OptionsList<E : Any>(
		owner: Owned
) : ContainerImpl(owner), Clearable, FocusContainer {

	constructor(owner: Owned, data: List<E?>) : this(owner) {
		data(data)
	}

	constructor(owner: Owned, data: ObservableList<E?>) : this(owner) {
		data(data)
	}

	override var focusOrder: Float = 0f

	/**
	 * If true, search sorting and item selection will be case insensitive.
	 */
	var caseInsensitive = true

	private val _input = own(Signal0())

	/**
	 * Dispatched on each input character.
	 * This does not dispatch when selecting an item from the drop down list.
	 */
	val input: Signal<() -> Unit>
		get() = _input

	private val _changed = own(Signal0())

	/**
	 * Dispatched on value commit.
	 * It is dispatched when the user selects an item, or commits the value of the text input. It is not dispatched
	 * when the selected item or text is programmatically changed.
	 */
	val changed: Signal<() -> Unit>
		get() = _changed

	/**
	 * The formatter to be used when converting a data element to a string.
	 * This should generally be the same formatter used for the labels in the ItemRenderer elements.
	 */
	var formatter: StringFormatter<E> = ToStringFormatter

	/**
	 * Given the text input's text, returns the matching item in the data list, or null if there are no matches.
	 * By default this will search for a case insensitive match to the item's string result from the [formatter].
	 */
	var textToItem = { text: String ->
		val textLower = text.toLowerCase()
		data.firstOrNull {
			if (it != null)
				formatter.format(it).toLowerCase() == textLower
			else false
		}
	}

	/**
	 * Sets the currently selected item.
	 * Note that this does not invoke [input] or [changed] signals.
	 */
	var selectedItem: E?
		get() = dataScroller.selection.selectedItem
		set(value) {
			dataScroller.selection.selectedItem = value
			textInput.text = if (value == null) "" else formatter.format(value)
		}

	private val textInput: TextInput = textInput {
		input.add {
			dataView.dirty()  // Most data views will change based on the text input.
			open()
			scrollModel.value = 0f // Scroll to the top.
			setSelectedItemFromText()
			_input.dispatch()
		}
	}

	var editable: Boolean by observable(true) {
		textInput.editable = it
	}

	private var background: UiComponent? = null
	private var downArrow: UiComponent? = null

	private val dataView = ListView<E>()

	private val dataScroller = vDataScroller<E> {
		keyDown().add(this@OptionsList::keyDownHandler)
		selection.changed.add { _, newSelection ->
			val value = newSelection.firstOrNull()
			textInput.text = if (value == null) "" else formatter.format(value)
			this@OptionsList.focusFirst()
			close()
			_changed.dispatch()
		}
	}

	private val listLift = lift {
		focusEnabled = true
		+dataScroller layout { fill() }
		onClosed = {
			close()
		}
	}

	/**
	 * The maximum number of full renderers that may be displayed at once.
	 */
	var maxItems: Int
		get() = dataScroller.maxItems
		set(value) {
			dataScroller.maxItems = value
		}

	val style = bind(OptionsListStyle())

	val dataScrollerStyle: DataScrollerStyle
		get() = dataScroller.style

	val dataScrollerLayoutStyle: VirtualVerticalLayoutStyle
		get() = dataScroller.layoutStyle

	private val defaultSortComparator = { o1: E, o2: E ->
		var str = textInput.text
		if (caseInsensitive) str = str.toLowerCase()
		val score1 = scoreBySearchIndex(o1, str)
		val score2 = scoreBySearchIndex(o2, str)
		score1.compareTo(score2)
	}

	/**
	 * Sorts the list.
	 * The default is to sort based on the text input's text compared to the position of the found text within
	 * the formatted element via [formatter].
	 * This does not modify the original list.
	 */
	var sortComparator: SortComparator<E>?
		get() = dataView.sortComparator
		set(value) {
			dataView.sortComparator = value
		}

	/**
	 * Filters the list.
	 * This does not modify the original list.
	 */
	var filter: Filter<E>?
		get() = dataView.filter
		set(value) {
			dataView.filter = value
		}

	/**
	 * The scroll model for the dropdown list.
	 */
	val scrollModel: ScrollModel
		get() = dataScroller.scrollModel

	fun rendererFactory(value: ItemRendererOwner<VerticalLayoutData>.() -> ListItemRenderer<E>) {
		dataScroller.rendererFactory(value)
	}

	/**
	 * Sets the nullRenderer factory for this list. The nullRenderer factory is responsible for creating nullRenderers
	 * to be used in this list.
	 */
	fun nullRendererFactory(value: ItemRendererOwner<VerticalLayoutData>.() -> ListRenderer) {
		dataScroller.nullRendererFactory(value)
	}

	private var dataBinding: Disposable? = null

	private fun unbindData() {
		dataBinding?.dispose()
		dataBinding = null
	}

	val data: List<E?>
		get() = dataScroller.data

	fun data(value: List<E?>) {
		unbindData()
		dataScroller.data(value)
		setSelectedItemFromText()
	}

	fun data(value: ObservableList<E?>) {
		unbindData()
		dataScroller.data(value)
		setSelectedItemFromText()
		dataBinding = value.bind {
			setSelectedItemFromText()
		}
	}

	private fun setSelectedItemFromText() {
		val item = textToItem(text)
		dataScroller.selection.selectedItem = item
		if (item != null)
			dataScroller.highlighted.selectedItem = item
	}

	fun emptyListRenderer(value: ItemRendererOwner<VerticalLayoutData>.() -> UiComponent) {
		dataScroller.emptyListRenderer(value)
	}

	init {
		styleTags.add(OptionsList)
		maxItems = 10
		addChild(textInput)

		keyDown().add(this::keyDownHandler)

		sortComparator = defaultSortComparator

		watch(style) {
			background?.dispose()
			background = addOptionalChild(0, it.background(this))

			downArrow?.dispose()
			downArrow = addChild(it.downArrow(this))
			downArrow!!.mouseDown().add {
				// Using mouseDown instead of click because we close on blur (which is often via mouseDown).
				open()
			}
			downArrow?.interactivityMode = if (_isOpen) InteractivityMode.NONE else InteractivityMode.ALL
		}

		inject(FocusManager).focusedChanged.add(this::focusChangedHandler)
	}

	private fun focusChangedHandler(old: Focusable?, new: Focusable?) {
		if (new == null || !owns(new)) {
			close()
			_changed.dispatch()
		}
	}

	private fun keyDownHandler(event: KeyInteractionRo) {
		if (event.defaultPrevented()) return
		when (event.keyCode) {
			Ascii.ESCAPE -> {
				event.handled = true
				event.preventDefault() // Prevent focus manager from setting focus back to the stage.
				focusFirst()
				close()
			}
			Ascii.RETURN, Ascii.ENTER -> {
				event.handled = true
				val newSelectedItem = dataScroller.highlighted.selectedItem ?: textToItem(text)
				if (newSelectedItem != selectedItem) {
					selectedItem = newSelectedItem
					focusFirst()
					close()
				}
				_changed.dispatch()
			}
			Ascii.DOWN -> {
				event.handled = true
				if (!_isOpen)
					open()
				highlightNext(1)
			}
			Ascii.UP -> {
				event.handled = true
				highlightPrevious(1)
			}
			Ascii.PAGE_DOWN -> {
				event.handled = true
				highlightNext((data.size - dataScroller.scrollMax).toInt())
			}
			Ascii.PAGE_UP -> {
				event.handled = true
				highlightPrevious((data.size - dataScroller.scrollMax).toInt())
			}
			Ascii.HOME -> {
				event.handled = true
				highlightFirst()
			}
			Ascii.END -> {
				event.handled = true
				highlightLast()
			}
		}
	}

	private fun highlightNext(delta: Int) {
		if (delta <= 0) return
		if (!_isOpen) return
		val highlighted = dataScroller.highlighted.selectedItem
		val selectedIndex = if (highlighted == null) -1 else data.indexOf(highlighted)
		val nextIndex = minOf(data.lastIndex, selectedIndex + delta)
		val nextIndexNotNull = data.indexOfFirst2(nextIndex) { it != null }
		if (nextIndexNotNull != -1) {
			dataScroller.highlighted.selectedItem = data[nextIndexNotNull]
			scrollTo(nextIndexNotNull.toFloat())
			dataScroller.focus()
		}
	}

	private fun highlightPrevious(delta: Int) {
		if (delta <= 0) return
		if (!_isOpen) return
		val highlighted = dataScroller.highlighted.selectedItem
		val selectedIndex = if (highlighted == null) data.size else data.indexOf(highlighted)
		val previousIndex = maxOf(0, selectedIndex - delta)
		val previousIndexNotNull = data.indexOfLast2(previousIndex) { it != null }
		if (previousIndexNotNull != -1) {
			dataScroller.highlighted.selectedItem = data[previousIndexNotNull]
			scrollTo(previousIndexNotNull.toFloat())
			dataScroller.focus()
		}
	}

	private fun highlightLast() {
		if (!_isOpen) return
		val lastIndexNotNull = data.indexOfLast2 { it != null }
		if (lastIndexNotNull != -1) {
			dataScroller.highlighted.selectedItem = data[lastIndexNotNull]
			scrollTo(lastIndexNotNull.toFloat())
			dataScroller.focus()
		}
	}

	private fun highlightFirst() {
		if (!_isOpen) return
		val firstIndexNotNull = data.indexOfFirst2 { it != null }
		if (firstIndexNotNull != -1) {
			dataScroller.highlighted.selectedItem = data[firstIndexNotNull]
			scrollTo(firstIndexNotNull.toFloat())
			dataScroller.focus()
		}
	}

	/**
	 * Scrolls the minimum distance to show the given bounding rectangle.
	 */
	private fun scrollTo(index: Float) {
		if (index < scrollModel.value)
			scrollModel.value = index
		val pageSize = data.size - dataScroller.scrollMax
		if (index + 1f > scrollModel.value + pageSize)
			scrollModel.value = index + 1f - pageSize
	}

	private fun scoreBySearchIndex(obj: E, str: String): Int {
		var itemStr = formatter.format(obj)
		if (caseInsensitive) itemStr = itemStr.toLowerCase()
		val i = itemStr.indexOf(str)
		if (i == -1) return 10000
		return i
	}

	private var _isOpen = false

	fun open() {
		if (_isOpen) return
		_isOpen = true
		dataScroller.highlighted.clear()
		addChild(listLift)
		textInput.focus()
	}

	fun close() {
		if (!_isOpen) return
		_isOpen = false
		removeChild(listLift)
	}

	fun toggleOpen() {
		if (_isOpen) close()
		else open()
	}

	var text: String
		get() = textInput.text
		set(value) {
			textInput.text = value
		}

	var listWidth: Float? by validationProp(null, ValidationFlags.LAYOUT)
	var listHeight: Float? by validationProp(null, ValidationFlags.LAYOUT)

	override fun updateLayout(explicitWidth: Float?, explicitHeight: Float?, out: Bounds) {
		val pad = style.padding
		val w = pad.reduceWidth(explicitWidth)
		val h = pad.reduceHeight(explicitHeight)
		val downArrow = this.downArrow!!
		textInput.setSize(if (w == null) null else w - style.gap - downArrow.width, h)
		textInput.setPosition(pad.left, pad.top)
		downArrow.moveTo(pad.left + textInput.width + style.gap, pad.top + (textInput.height - downArrow.height) * 0.5f)
		out.set(pad.expandWidth2(textInput.width + style.gap + downArrow.width), pad.expandHeight2(maxOf(textInput.height, downArrow.height)))
		background?.setSize(out.width, out.height)

		listLift.setSize(listWidth ?: out.width, listHeight)
		listLift.moveTo(0f, out.height)
	}

	override fun clear() {
		textInput.clear()
		selectedItem = null
	}

	override fun dispose() {
		unbindData()
		inject(FocusManager).focusedChanged.remove(this::focusChangedHandler)
		close()
		super.dispose()
	}

	companion object : StyleTag
}

class OptionsListStyle : StyleBase() {
	override val type: StyleType<OptionsListStyle> = OptionsListStyle

	/**
	 * The background of the text input / down arrow area.
	 * Skins should ensure the text input doesn't have a background.
	 */
	var background by prop(noSkinOptional)

	/**
	 * The padding between the background and the text input / down arrow area.
	 */
	var padding by prop(Pad(0f))

	var downArrow by prop(noSkin)

	/**
	 * The gap between the down arrow and the text field.
	 */
	var gap by prop(2f)

	companion object : StyleType<OptionsListStyle>
}

fun <E : Any> Owned.optionsList(
		init: ComponentInit<OptionsList<E>> = {}): OptionsList<E> {
	val t = OptionsList<E>(this)
	t.init()
	return t
}

fun <E : Any> Owned.optionsList(
		data: ObservableList<E?>,
		rendererFactory: LayoutDataProvider<VerticalLayoutData>.() -> ListItemRenderer<E> = { simpleItemRenderer() },
		init: ComponentInit<OptionsList<E>> = {}): OptionsList<E> {
	val t = OptionsList<E>(this)
	t.data(data)
	t.rendererFactory(rendererFactory)
	t.init()
	return t
}

fun <E : Any> Owned.optionsList(
		data: List<E?>,
		rendererFactory: LayoutDataProvider<VerticalLayoutData>.() -> ListItemRenderer<E> = { simpleItemRenderer() },
		init: ComponentInit<OptionsList<E>> = {}): OptionsList<E> {
	val t = OptionsList<E>(this)
	t.data(data)
	t.rendererFactory(rendererFactory)
	t.init()
	return t
}
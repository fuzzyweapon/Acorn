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

package com.acornui.gl.component.text

import com.acornui._assert
import com.acornui.collection.*
import com.acornui.component.*
import com.acornui.component.layout.algorithm.FlowHAlign
import com.acornui.component.layout.algorithm.FlowVAlign
import com.acornui.component.layout.algorithm.LineInfo
import com.acornui.component.layout.algorithm.LineInfoRo
import com.acornui.component.style.*
import com.acornui.component.text.CharStyle
import com.acornui.component.text.TextField
import com.acornui.component.text.TextFlowStyle
import com.acornui.core.*
import com.acornui.core.cursor.RollOverCursor
import com.acornui.core.cursor.StandardCursors
import com.acornui.core.di.Owned
import com.acornui.core.di.inject
import com.acornui.core.graphics.BlendMode
import com.acornui.core.input.interaction.DragInteraction
import com.acornui.core.input.interaction.dragAttachment
import com.acornui.core.selection.Selectable
import com.acornui.core.selection.SelectionManager
import com.acornui.core.selection.SelectionRange
import com.acornui.gl.core.GlState
import com.acornui.gl.core.pushQuadIndices
import com.acornui.graphics.Color
import com.acornui.graphics.ColorRo
import com.acornui.math.Bounds
import com.acornui.math.MathUtils.floor
import com.acornui.math.MathUtils.round
import com.acornui.math.Matrix4Ro
import com.acornui.math.Vector3
import com.acornui.math.ceil
import com.acornui.observe.bind
import com.acornui.string.isBreaking

/**
 * A TextField implementation for the OpenGL back-ends.
 * @author nbilyk
 */
@Suppress("LeakingThis", "UNUSED_PARAMETER")
open class GlTextField(owner: Owned) : ContainerImpl(owner), TextField {

	override final val flowStyle = bind(TextFlowStyle())
	override final val charStyle = bind(CharStyle())

	private val selectionManager = inject(SelectionManager)

	protected var _selectionCursor: RollOverCursor? = null

	private val drag = dragAttachment(0f)

	/**
	 * The Selectable target to use for the selection range.
	 */
	var selectionTarget: Selectable = this

	private val _textSpan = span()
	private val _textContents = textFlow { +_textSpan }
	protected var _contents: UiComponent = addChild(_textContents)

	/**
	 * The TextField contents.
	 */
	override var contents: UiComponent
		get() = _contents
		set(value) {
			if (_contents == value) return
			removeChild(_contents)
			_contents = value
			addChild(value)
		}

	private val _leaves = ArrayList<TextFieldLeaf>()

	val leaves: List<TextFieldLeaf>
		get() {
			validate(ValidationFlags.HIERARCHY_ASCENDING)
			return _leaves
		}

	init {
		addStyleRule(flowStyle)
		addStyleRule(charStyle)
		styleTags.add(TextField)
		BitmapFontRegistry.fontRegistered.add(this::fontRegisteredHandler)

		watch(charStyle) {
			refreshCursor()
			drag.enabled = it.selectable
		}

		validation.addNode(TextValidationFlags.SELECTION, ValidationFlags.HIERARCHY_ASCENDING, this::updateSelection)

		selectionManager.selectionChanged.add(this::selectionChangedHandler)

		drag.drag.add(this::dragHandler)

	}

	override fun updateHierarchyAscending() {
		_leaves.clear()
		_contents.childWalkLevelOrder<UiComponent> {
			if (it is TextFieldLeaf)
				_leaves.add(it)
			TreeWalk.CONTINUE
		}
	}

	private fun selectionChangedHandler(old: List<SelectionRange>, new: List<SelectionRange>) {
		invalidate(TextValidationFlags.SELECTION)
	}

	private fun dragHandler(event: DragInteraction) {
		selectionManager.selection = getNewSelection(event) ?: emptyList()
	}

	private fun getNewSelection(event: DragInteraction): List<SelectionRange>? {
		val startElement = event.startElement ?: return null
		val leaf = startElement.parentWalk { it !is TextFieldLeaf } as TextFieldLeaf? ?: return null
		var rangeStart = 0
		for (i in 0.._leaves.lastIndex) {
			val iLeaf = _leaves[i]
			if (iLeaf == leaf) break
			rangeStart += iLeaf.parts.size
		}

		val p1 = event.startPositionLocal
		val p2 = event.positionLocal
		val p1A = rangeStart + leaf.getSelectionIndex(p1.x - leaf.x, p1.y - leaf.y)
		val p2A = rangeStart + leaf.getSelectionIndex(p2.x - leaf.x, p2.y - leaf.y)
		if (p2A > p1A) {
			return listOf(SelectionRange(selectionTarget, p1A, p2A))
		} else {
			return listOf(SelectionRange(selectionTarget, p2A, p1A))
		}
	}

	protected open fun refreshCursor() {
		if (charStyle.selectable) {
			if (_selectionCursor == null)
				_selectionCursor = RollOverCursor(this, StandardCursors.IBEAM)
		} else {
			_selectionCursor?.dispose()
			_selectionCursor = null
		}
	}

	override var text: String
		get() {
			val leaves = leaves
			val builder = StringBuilder()
			for (i in 0..leaves.lastIndex) {
				val leaf = leaves[i]
				for (j in 0..leaf.parts.lastIndex) {
					val char = leaf.parts[j].char
					if (char != null)
						builder.append(char)
				}
			}
			return builder.toString()
		}
		set(value) {
			_textSpan.text = value
			contents = _textContents
		}

	override var htmlText: String?
		get() = ""
		set(value) {
		}

	private fun fontRegisteredHandler(registeredFont: BitmapFont) {
		invalidateStyles()
	}

	protected open fun updateSelection() {
		var rangeStart = 0
		for (i in 0.._leaves.lastIndex) {
			val leaf = _leaves[i]
			leaf.setSelection(rangeStart, selectionManager.selection.filter { it.target == selectionTarget })
			rangeStart += leaf.parts.size
		}
	}

	override fun updateLayout(explicitWidth: Float?, explicitHeight: Float?, out: Bounds) {
		val contents = _contents
		contents.setSize(explicitWidth, explicitHeight)
		out.set(contents.bounds)

		val lineHeight = BitmapFontRegistry.getFont(charStyle)?.data?.lineHeight?.toFloat() ?: 0f
		if (out.height < lineHeight) out.height = lineHeight

		if (explicitWidth != null) out.width = explicitWidth
		if (explicitHeight != null) out.height = explicitHeight
	}

	override fun dispose() {
		super.dispose()
		BitmapFontRegistry.fontRegistered.remove(this::fontRegisteredHandler)
		_selectionCursor?.dispose()
		_selectionCursor = null
		selectionManager.selectionChanged.remove(this::selectionChangedHandler)
	}
}

object TextValidationFlags {
	const val SELECTION = 1 shl 16
	const val VERTICES = 1 shl 17
}

class TfCharStyle {
	var font: BitmapFont? = null
	val selectedTextColorTint: Color = Color()
	val selectedBackgroundColor: Color = Color()
	val textColorTint: Color = Color()
	val backgroundColor: Color = Color()
}

interface TextSpanElement : ElementParent<TextElement>, Styleable {
	var parent: UiComponent?
	val font: BitmapFont?

	fun validateStyles()
	fun setColorTint(concatenatedColorTint: ColorRo)

	fun char(char: Char): TextElement

	operator fun String?.unaryPlus() {
		if (this == null) return
		for (i in 0..length - 1) {
			addElement(char(this[i]))
		}
	}
}

class TextSpanElementImpl : TextSpanElement {

	private val _styleTags = ActiveList<StyleTag>()
	override val styleTags: MutableList<StyleTag>
		get() = _styleTags
	private val _styleRules = ActiveList<StyleRule<*>>()
	override val styleRules: MutableList<StyleRule<*>>
		get() = _styleRules

	override fun <T : Style> getRulesByType(type: StyleType<T>, out: MutableList<StyleRule<T>>) {
		out.clear()
		@Suppress("UNCHECKED_CAST")
		(styleRules as Iterable<StyleRule<T>>).filterTo(out, { it.style.type == type })
	}

	override var parent: UiComponent? = null

	override val styleParent: Styleable?
		get() = parent

	override fun invalidateStyles() {
		styleParent?.invalidateStyles()
	}

	private val _elements = ArrayList<TextElement>()
	override val elements: List<TextElement>
		get() = _elements

	val charStyle = CharStyle()

	private val tfCharStyle = TfCharStyle()

	private val bubblingFlags = ValidationFlags.HIERARCHY_ASCENDING or ValidationFlags.LAYOUT

	init {
		_styleTags.bind(this::invalidateStyles)
		_styleRules.bind(this::invalidateStyles)
	}

	override val font: BitmapFont?
		get() = tfCharStyle.font

	operator fun Char?.unaryPlus() {
		if (this == null) return
		addElement(char(this))
	}

	override fun <S : TextElement> addElement(index: Int, element: S): S {
		element.parent = this
		_elements.add(index, element)
		parent?.invalidate(bubblingFlags)
		return element
	}

	override fun removeElement(index: Int): TextElement {
		val element = _elements.removeAt(index)
		element.parent = null
		parent?.invalidate(bubblingFlags)
		return element
	}

	/**
	 * Clears all elements in this span.
	 * @param dispose If dispose is true, the elements will be disposed.
	 */
	override fun clearElements(dispose: Boolean) {
		for (i in 0.._elements.lastIndex) {
			val e = _elements[i]
			e.parent = null
			if (dispose) e.dispose()
		}
		_elements.clear()
		parent?.invalidate(bubblingFlags)
	}

	override fun validateStyles() {
		CascadingStyleCalculator.calculate(charStyle, this)
		tfCharStyle.font = BitmapFontRegistry.getFont(charStyle)
	}

	override fun setColorTint(concatenatedColorTint: ColorRo) {
		tfCharStyle.selectedTextColorTint.set(concatenatedColorTint).mul(charStyle.selectedColorTint)
		tfCharStyle.selectedBackgroundColor.set(concatenatedColorTint).mul(charStyle.selectedBackgroundColor)
		tfCharStyle.textColorTint.set(concatenatedColorTint).mul(charStyle.colorTint)
		tfCharStyle.backgroundColor.set(concatenatedColorTint).mul(charStyle.backgroundColor)
	}

	override fun char(char: Char): TextElement {
		return TfChar.obtain(char, tfCharStyle)
	}
}

/**
 * A utility variable that when set, clears/disposes the current elements and replaces them with the new text.
 */
var TextSpanElement.text: String
	get() {
		val elements = elements
		val builder = StringBuilder()
		for (i in 0..elements.lastIndex) {
			val char = elements[i].char
			if (char != null)
				builder.append(char)
		}
		return builder.toString()
	}
	set(value) {
		clearElements(true)
		+value
	}

/**
 * The smallest unit that can be inside of a TextField.
 * This will generally represent a single character, but may be more complex components.
 */
interface TextElement : Disposable {

	/**
	 * Set by the TextSpanElement when this is part is added.
	 */
	var parent: TextSpanElement?

	val char: Char?

	var x: Float
	var y: Float

	/**
	 * The amount of horizontal space to advance after this part.
	 */
	val xAdvance: Float

	/**
	 * The height of the text line.
	 */
	val lineHeight: Float

	/**
	 * If the [TextFlowStyle] vertical alignment is BASELINE, this property will be used to vertically align the
	 * elements.
	 */
	val baseline: Float

	/**
	 * If set, this part should be drawn to fit this width.
	 */
	var explicitWidth: Float?

	/**
	 * The explicit width, if it's set, or the xAdvance.
	 */
	val width: Float
		get() = explicitWidth ?: xAdvance

	/**
	 * Returns the amount of horizontal space to offset this part from the next part.
	 */
	fun getKerning(next: TextElement): Float

	/**
	 * If true, this element will cause the line to break after this element.
	 */
	val clearsLine: Boolean

	/**
	 * If true, the tabstop should be cleared after placing this element.
	 */
	val clearsTabstop: Boolean

	/**
	 * If true, this part may be used as a word break. (The part after this part may be placed on the next line).
	 */
	val isBreaking: Boolean

	/**
	 * If true, this part will not cause a wrap.
	 */
	val overhangs: Boolean

	/**
	 * If set to true, this part will be rendered using the selected styling.
	 */
	fun setSelected(value: Boolean)

	/**
	 * Finalizes the vertices for rendering.
	 */
	fun validateVertices(transform: Matrix4Ro, leftClip: Float, topClip: Float, rightClip: Float, bottomClip: Float)

	/**
	 * Draws this part.
	 */
	fun render(glState: GlState)

}

fun span(init: ComponentInit<TextSpanElement> = {}): TextSpanElementImpl {
	val s = TextSpanElementImpl()
	s.init()
	return s
}

interface TextFieldLeaf : UiComponent, ElementParent<TextSpanElement> {

	/**
	 * The span parts this leaf contains.
	 */
	val parts: List<TextElement>

	/**
	 * @param x The relative x coordinate
	 * @param y The relative y coordinate
	 * @return Returns the relative index of the object nearest [x,y]. The object index will be separated at the half-width of
	 * the character. This range will be between [0, size]
	 */
	fun getSelectionIndex(x: Float, y: Float): Int
}

/**
 * Sets the text selection.
 * @param rangeStart The starting index of this leaf.
 * @param selection A list of ranges that are selected.
 */
fun TextFieldLeaf.setSelection(rangeStart: Int, selection: List<SelectionRange>) {
	val parts = this.parts
	for (i in 0..parts.lastIndex) {
		val selected = selection.indexOfFirst2 { it.contains(i + rangeStart) } != -1
		parts[i].setSelected(selected)
	}
}

/**
 * A TextFlow component is a container of styleable text spans, to be used inside of a TextField.
 */
class TextFlow(owner: Owned) : ContainerImpl(owner), TextFieldLeaf {

	val flowStyle = bind(TextFlowStyle())

	init {
		validation.addNode(TextValidationFlags.VERTICES, ValidationFlags.LAYOUT or ValidationFlags.CONCATENATED_TRANSFORM or ValidationFlags.STYLES, 0, this::updateVertices)
	}

	private val _elements = ArrayList<TextSpanElement>()
	override val elements: List<TextSpanElement>
		get() = _elements

	private val _lines = ArrayList<LineInfo>()

	/**
	 * The list of current lines. This is valid after a layout.
	 */
	val lines: List<LineInfoRo>
		get() = _lines

	private val _parts = ArrayList<TextElement>()

	override val parts: List<TextElement>
		get() {
			validate(ValidationFlags.HIERARCHY_ASCENDING)
			return _parts
		}

	@Suppress("FINAL_UPPER_BOUND")
	override fun <S : TextSpanElement> addElement(index: Int, element: S): S {
		_assert(element.styleParent == null)
		_elements.add(index, element)
		element.parent = this
		invalidate(bubblingFlags)
		return element
	}

	override fun removeElement(index: Int): TextSpanElement {
		val element = _elements.removeAt(index)
		element.parent = null
		invalidate(bubblingFlags)
		return element
	}

	override fun clearElements(dispose: Boolean) {
		val c = _elements
		while (c.isNotEmpty()) {
			removeElement(_elements.lastIndex)
		}
	}

	override fun updateHierarchyAscending() {
		super.updateHierarchyAscending()
		_parts.clear()
		for (i in 0.._elements.lastIndex) {
			_parts.addAll(_elements[i].elements)
		}
	}

	override fun updateStyles() {
		super.updateStyles()
		for (i in 0.._elements.lastIndex) {
			_elements[i].validateStyles()
		}
	}

	override fun updateLayout(explicitWidth: Float?, explicitHeight: Float?, out: Bounds) {
		val padding = flowStyle.padding
		val availableWidth: Float? = padding.reduceWidth(explicitWidth)

		_lines.freeTo(linesPool)
		// Calculate lines
		var x = 0f
		var currentLine = linesPool.obtain()

		var spanPartIndex = 0
		while (spanPartIndex < _parts.size) {
			val part = _parts[spanPartIndex]
			part.x = x

			val partW = part.xAdvance

			// If this is multiline text and we extend beyond the right edge,then push the current line and start a new one.
			val extendsEdge = flowStyle.multiline && (!part.overhangs && availableWidth != null && x + partW > availableWidth)
			val isLast = spanPartIndex == _parts.lastIndex
			if (isLast || part.clearsLine || extendsEdge) {
				if (isLast) {
					spanPartIndex++
					currentLine.endIndex = spanPartIndex
				} else {
					// Find the last good breaking point.
					var breakIndex = _parts.indexOfLast2(spanPartIndex, currentLine.startIndex) { it.isBreaking }
					if (breakIndex == -1) breakIndex = spanPartIndex - 1
					val endIndex = _parts.indexOfFirst2(breakIndex + 1, spanPartIndex) { !it.overhangs }
					currentLine.endIndex = if (endIndex == -1) spanPartIndex + 1
					else endIndex
					spanPartIndex = currentLine.endIndex
				}
				_lines.add(currentLine)
				currentLine = linesPool.obtain()
				currentLine.startIndex = spanPartIndex
				x = 0f
			} else {
				val nextPart = _parts.getOrNull(spanPartIndex + 1)
				val kerning = if (nextPart == null) 0f else part.getKerning(nextPart)
				x += partW + kerning

				if (part.clearsTabstop) {
					val font = part.parent!!.font
					if (font != null) {
						val spaceSize = (font.data.glyphs[' ']?.advanceX?.toFloat() ?: 6f)
						val tabSize = spaceSize * flowStyle.tabSize
						val tabIndex = floor(x / tabSize) + 1
						var w = tabIndex * tabSize - x
						if (w < spaceSize) w += tabSize
						part.explicitWidth = w
						x += w
					}
				}
				spanPartIndex++
			}
		}
		linesPool.free(currentLine)

		var y = padding.top
		var measuredWidth = 0f
		for (i in 0.._lines.lastIndex) {
			val line = _lines[i]
			line.y = y

			var belowBaseline = 0f
			for (j in line.startIndex..line.endIndex - 1) {
				val part = _parts[j]
				val b = part.lineHeight - part.baseline
				if (b > belowBaseline) belowBaseline = b
				if (part.baseline > line.baseline) line.baseline = part.baseline
				if (!part.overhangs) line.width = part.x + part.xAdvance
			}
			line.height = line.baseline + belowBaseline
			if (line.width > measuredWidth)
				measuredWidth = line.width
			positionPartsInLine(line, availableWidth)
			y += line.height + flowStyle.verticalGap
		}
		val measuredHeight = y - flowStyle.verticalGap + padding.bottom
		measuredWidth += padding.left + padding.right
		if (measuredWidth > out.width) out.width = measuredWidth
		if (measuredHeight > out.height) out.height = measuredHeight
	}

	private fun positionPartsInLine(line: LineInfoRo, availableWidth: Float?) {
		val xOffset: Float
		if (availableWidth != null) {
			val remainingSpace = availableWidth - line.width
			xOffset = flowStyle.padding.left + when (flowStyle.horizontalAlign) {
				FlowHAlign.LEFT -> 0f
				FlowHAlign.CENTER -> (remainingSpace * 0.5f).floor()
				FlowHAlign.RIGHT -> remainingSpace
				FlowHAlign.JUSTIFY -> 0f
			}

			if (flowStyle.horizontalAlign == FlowHAlign.JUSTIFY &&
					line.size > 1 &&
					_lines.last() != line &&
					!_parts[line.endIndex - 1].clearsLine
					) {
				// Apply JUSTIFY spacing if this is not the last line, and there are more than one elements.
				val lastIndex = _parts.indexOfLast2(line.endIndex - 1, line.startIndex) { !it.overhangs }
				val numSpaces = _parts.count2(line.startIndex, lastIndex) { it.char == ' ' }
				if (numSpaces > 0) {
					val hGap = remainingSpace / numSpaces
					var justifyOffset = 0f
					for (i in line.startIndex..line.endIndex - 1) {
						val part = _parts[i]
						part.x = (part.x + justifyOffset).floor()
						if (i < lastIndex && part.char == ' ') {
							part.explicitWidth = part.xAdvance + hGap.ceil()
							justifyOffset += hGap
						}
					}
				}
			}
		} else {
			xOffset = flowStyle.padding.left
		}

		for (i in line.startIndex..line.endIndex - 1) {
			val part = _parts[i]

			val yOffset = when (flowStyle.verticalAlign) {
				FlowVAlign.TOP -> 0f
				FlowVAlign.MIDDLE -> round((line.height - part.lineHeight) * 0.5f).toFloat()
				FlowVAlign.BOTTOM -> line.height - part.lineHeight
				FlowVAlign.BASELINE -> line.baseline - part.baseline
			}

			part.x += xOffset
			part.y = line.y + yOffset
		}
	}

	private fun updateVertices() {
		val padding = flowStyle.padding
		val leftClip = padding.left
		val topClip = padding.top
		val rightClip = (explicitWidth ?: Float.MAX_VALUE) - padding.right
		val bottomClip = (explicitHeight ?: Float.MAX_VALUE) - padding.bottom
		for (i in 0.._parts.lastIndex) {
			_parts[i].validateVertices(concatenatedTransform, leftClip, topClip, rightClip, bottomClip)
		}
	}

	override fun getSelectionIndex(x: Float, y: Float): Int {
		if (lines.isEmpty()) return 0
		if (y < lines.first().y) return 0
		if (y >= lines.last().bottom) return _parts.size
		val lineIndex = _lines.sortedInsertionIndex(y, {
			y, line ->
			y.compareTo(line.bottom)
		})
		val line = _lines[lineIndex]
		return _parts.sortedInsertionIndex(x, {
			x, part ->
			x.compareTo(part.x + (part.width) / 2f)
		}, line.startIndex, line.endIndex)
	}

	override fun updateConcatenatedColorTransform() {
		super.updateConcatenatedColorTransform()
		for (i in 0.._elements.lastIndex) {
			_elements[i].setColorTint(_concatenatedColorTint)
		}
	}

	private val glState = inject(GlState)

	override fun draw() {
		glState.camera(camera)
		for (i in 0.._parts.lastIndex) {
			_parts[i].render(glState)
		}
		super.draw()
	}

	companion object {
		private val linesPool = ClearableObjectPool { LineInfo() }
	}
}

private fun Owned.textFlow(init: ComponentInit<TextFlow>): TextFlow {
	val f = TextFlow(this)
	f.init()
	return f
}

/**
 * Represents a single character, typically within a [TextSpanElement].
 */
class TfChar private constructor() : TextElement, Clearable {

	override var char: Char = charPlaceholder
	var style: TfCharStyle? = null

	override var parent: TextSpanElement? = null

	val glyph: Glyph?
		get() {
			return style?.font?.getGlyphSafe(char)
		}

	override var x: Float = 0f
	override var y: Float = 0f

	override val xAdvance: Float
		get() = (glyph?.advanceX?.toFloat() ?: 0f)

	override val lineHeight: Float
		get() = (parent?.font?.data?.lineHeight?.toFloat() ?: 0f)

	override val baseline: Float
		get() = (parent?.font?.data?.baseLine?.toFloat() ?: 0f)

	override var explicitWidth: Float? = null

	override fun getKerning(next: TextElement): Float {
		val d = glyph?.data ?: return 0f
		val c = next.char ?: return 0f
		return d.getKerning(c).toFloat()
	}

	private var u = 0f
	private var v = 0f
	private var u2 = 0f
	private var v2 = 0f

	private var visible = true

	/**
	 * A cache of the vertex positions in world space.
	 */
	private val vertexPoints: Array<Vector3> = arrayOf(Vector3(), Vector3(), Vector3(), Vector3())
	private val normal = Vector3()

	private val backgroundVertices: Array<Vector3> = arrayOf(Vector3(), Vector3(), Vector3(), Vector3())

	private var fontColor = Color.BLACK
	private var backgroundColor = Color.CLEAR

	override val clearsLine: Boolean
		get() = char == '\n'
	override val clearsTabstop: Boolean
		get() = char == '\t'
	override val isBreaking: Boolean
		get() = char.isBreaking()
	override val overhangs: Boolean
		get() = char == ' '

	override fun setSelected(value: Boolean) {
		val style = style ?: return
		if (value) {
			fontColor = style.selectedTextColorTint
			backgroundColor = style.selectedBackgroundColor
		} else {
			fontColor = style.textColorTint
			backgroundColor = style.backgroundColor
		}
	}

	override fun validateVertices(transform: Matrix4Ro, leftClip: Float, topClip: Float, rightClip: Float, bottomClip: Float) {
		val x = x
		val y = y
		val glyph = glyph ?: return

		var charL = glyph.offsetX + x
		var charT = glyph.offsetY + y
		var charR = charL + glyph.width
		var charB = charT + glyph.height

		val bgL = maxOf(leftClip, x)
		val bgT = maxOf(topClip, y)
		val bgR = minOf(rightClip, x + width)
		val bgB = minOf(bottomClip, y + lineHeight)

		visible = bgL < rightClip && bgT < bottomClip && bgR > leftClip && bgB > topClip
		if (!visible)
			return

		val region = glyph.region
		val textureW = glyph.texture.width.toFloat()
		val textureH = glyph.texture.height.toFloat()

		var regionX = region.x.toFloat()
		var regionY = region.y.toFloat()
		var regionR = region.right.toFloat()
		var regionB = region.bottom.toFloat()

		if (charL < leftClip) {
			if (glyph.isRotated) regionY += leftClip - charL
			else regionX += leftClip - charL
			charL = leftClip
		}
		if (charT < topClip) {
			if (glyph.isRotated) regionX += topClip - charT
			else regionY -= topClip - charT
			charT = topClip
		}
		if (charR > rightClip) {
			if (glyph.isRotated) regionB -= charR - rightClip
			else regionR -= charR - rightClip
			charR = rightClip
		}
		if (charB > bottomClip) {
			if (glyph.isRotated) regionR -= charB - bottomClip
			else regionB -= charB - bottomClip
			charB = bottomClip
		}

		u = regionX / textureW
		v = regionY / textureH
		u2 = regionR / textureW
		v2 = regionB / textureH

		// Transform vertex coordinates from local to global
		transform.prj(vertexPoints[0].set(charL, charT, 0f))
		transform.prj(vertexPoints[1].set(charR, charT, 0f))
		transform.prj(vertexPoints[2].set(charR, charB, 0f))
		transform.prj(vertexPoints[3].set(charL, charB, 0f))

		// Background vertices
		transform.prj(backgroundVertices[0].set(bgL, bgT, 0f))
		transform.prj(backgroundVertices[1].set(bgR, bgT, 0f))
		transform.prj(backgroundVertices[2].set(bgR, bgB, 0f))
		transform.prj(backgroundVertices[3].set(bgL, bgB, 0f))

		transform.rot(normal.set(Vector3.NEG_Z)).nor()
	}

	override fun render(glState: GlState) {
		if (!visible) return
		val glyph = glyph ?: return
		val batch = glState.batch

		if (backgroundColor.a > 0f) {
			batch.begin()
			glState.setTexture(glState.whitePixel)
			glState.blendMode(BlendMode.NORMAL, false)
			// Top left
			batch.putVertex(backgroundVertices[0], normal, backgroundColor, 0f, 0f)
			// Top right
			batch.putVertex(backgroundVertices[1], normal, backgroundColor, 0f, 0f)
			// Bottom right
			batch.putVertex(backgroundVertices[2], normal, backgroundColor, 0f, 0f)
			// Bottom left
			batch.putVertex(backgroundVertices[3], normal, backgroundColor, 0f, 0f)
			batch.pushQuadIndices()
		}

		if (u == u2 || v == v2 || glyph.width <= 0f || glyph.height <= 0f) return // Nothing to draw
		batch.begin()
		glState.setTexture(glyph.texture)
		glState.blendMode(BlendMode.NORMAL, glyph.premultipliedAlpha)

		if (glyph.isRotated) {
			// Top left
			batch.putVertex(vertexPoints[0], normal, fontColor, u2, v)
			// Top right
			batch.putVertex(vertexPoints[1], normal, fontColor, u2, v2)
			// Bottom right
			batch.putVertex(vertexPoints[2], normal, fontColor, u, v2)
			// Bottom left
			batch.putVertex(vertexPoints[3], normal, fontColor, u, v)
		} else {
			// Top left
			batch.putVertex(vertexPoints[0], normal, fontColor, u, v)
			// Top right
			batch.putVertex(vertexPoints[1], normal, fontColor, u2, v)
			// Bottom right
			batch.putVertex(vertexPoints[2], normal, fontColor, u2, v2)
			// Bottom left
			batch.putVertex(vertexPoints[3], normal, fontColor, u, v2)
		}

		batch.pushQuadIndices()
	}

	override fun dispose() {
		pool.free(this)
	}

	override fun clear() {
		char = charPlaceholder
		style = null
		parent = null
	}

	companion object {
		private const val charPlaceholder = 0.toChar()
		private val pool = ClearableObjectPool { TfChar() }

		fun obtain(char: Char, charStyle: TfCharStyle): TfChar {
			val c = pool.obtain()
			c.char = char
			c.style = charStyle
			return c
		}
	}
}


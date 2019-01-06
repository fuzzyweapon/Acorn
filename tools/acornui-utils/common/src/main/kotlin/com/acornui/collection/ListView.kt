package com.acornui.collection

import com.acornui.core.Disposable
import com.acornui.reflect.observable
import com.acornui.signal.Signal
import com.acornui.signal.Signal0
import com.acornui.signal.Signal2
import com.acornui.signal.Signal3

/**
 * ListView is a read-only wrapper to an ObservableList that will allow filtering and sorting
 * without modifying the original list.
 */
class ListView<E>() : ObservableList<E>, Disposable {

	private var wrapped: List<E> = emptyList()
	private var observableWrapped: ObservableList<E>? = null

	constructor(source: List<E>) : this() {
		data(source)
	}

	constructor(source: ObservableList<E>) : this() {
		data(source)
	}

	/**
	 * A list of ordered and reduced indices mapping to the [wrapped] list.
	 */
	private val local = ArrayList<Int>()

	private var _added = Signal2<Int, E>()
	override val added = _added.asRo()

	private var _removed = Signal2<Int, E>()
	override val removed = _removed.asRo()

	private var _changed = Signal3<Int, E, E>()
	override val changed = _changed.asRo()

	private var _modified = Signal2<Int, E>()
	override val modified = _modified.asRo()

	private val _reset = Signal0()
	override val reset: Signal<() -> Unit> = _reset

	private var isDirty: Boolean = true

	val iteratorPool = ClearableObjectPool { ConcurrentListIteratorImpl(this) }

	/**
	 * If set, this list will be reduced to elements passing the given filter function.
	 * Changing this will dispatch a [reset] signal.
	 */
	var filter by observable<Filter<E>?>(null) {
		dirty()
		Unit
	}

	/**
	 * If set, this list view will become sorted.
	 * Changing this will dispatch a [reset] signal.
	 * Example: listView.sortComparator = { o1, o2 -> o1.compareTo(o2) }
	 */
	var sortComparator by observable<SortComparator<E>?>(null) {
		dirty()
		Unit
	}

	private val insertionComparator: SortComparator<Int> = { insertSourceIndex, sourceIndex ->
		val a = wrapped[insertSourceIndex]
		val b = wrapped[sourceIndex]
		val result = sortComparator!!(a, b)
		if (result == 0) insertSourceIndex.compareTo(sourceIndex)
		else result
	}

	private val sortComparatorObj: Comparator<Int> = Comparator { sourceIndexA, sourceIndexB ->
		val a = wrapped[sourceIndexA]
		val b = wrapped[sourceIndexB]
		val result = sortComparator!!(a, b)
		if (result == 0) sourceIndexA.compareTo(sourceIndexB)
		else result
	}

	fun data(source: List<E>) {
		if (wrapped === source) return
		unwatchWrappedList()
		wrapped = source
		dirty()
	}

	private fun unwatchWrappedList() {
		val old = observableWrapped ?: return
		old.added.add(this::addedHandler)
		old.removed.add(this::removedHandler)
		old.changed.add(this::changedHandler)
		old.modified.add(this::elementModifiedHandler)
		old.reset.add(this::dirty)
		observableWrapped = null
	}

	fun data(source: ObservableList<E>) {
		if (observableWrapped === source) return
		unwatchWrappedList()
		observableWrapped = source
		wrapped = source
		source.added.add(this::addedHandler)
		source.removed.add(this::removedHandler)
		source.changed.add(this::changedHandler)
		source.modified.add(this::elementModifiedHandler)
		source.reset.add(this::dirty)
		dirty()
	}

	private fun addedHandler(sourceIndex: Int, element: E) {
		if (isDirty) return
		if (sourceIndex != wrapped.lastIndex) {
			for (i in 0..local.lastIndex) {
				if (local[i] >= sourceIndex)
					local[i]++
			}
		}
		val localIndex = insertionIndex(sourceIndex)
		if (localIndex != -1) {
			local.add(localIndex, sourceIndex)
			_added.dispatch(localIndex, element)
		}
	}

	private fun removedHandler(sourceIndex: Int, element: E) {
		if (isDirty) return
		val localIndex = local.indexOf(sourceIndex)
		if (sourceIndex != wrapped.size) {
			for (i in 0..local.lastIndex) {
				if (local[i] >= sourceIndex)
					local[i]--
			}
		}
		if (localIndex != -1) {
			local.removeAt(localIndex)
			_removed.dispatch(localIndex, element)
		}
	}

	private fun changedHandler(sourceIndex: Int, old: E, new: E) {
		if (isDirty) return
		if (filter != null || sortComparator != null) {
			if (isLocalIndexCorrect(sourceIndex)) {
				_changed.dispatch(sourceIndex, old, new)
			} else {
				removedHandler(sourceIndex, old)
				addedHandler(sourceIndex, new)
			}
		} else {
			_changed.dispatch(sourceIndex, old, new)
		}
	}

	private fun elementModifiedHandler(sourceIndex: Int, element: E) {
		if (isDirty) return
		if (filter != null || sortComparator != null) {
			if (isLocalIndexCorrect(sourceIndex)) {
				_modified.dispatch(sourceIndex, element)
			} else {
				removedHandler(sourceIndex, element)
				addedHandler(sourceIndex, element)
			}
		} else {
			_modified.dispatch(sourceIndex, element)
		}
	}

	override fun notifyElementModified(index: Int) {
		validate()
		if (index < 0 || index >= size) return
		if (observableWrapped != null) {
			observableWrapped!!.notifyElementModified(local[index])
		} else {
			val localIndex = local[index]
			elementModifiedHandler(localIndex, wrapped[localIndex])
		}
	}

	private fun isLocalIndexCorrect(sourceIndex: Int): Boolean {
		val localIndex = local.indexOf(sourceIndex)
		val element = wrapped[sourceIndex]
		val isFiltered = filtered(element)
		val wasFiltered = localIndex == -1
		if (isFiltered != wasFiltered) return false
		if (isFiltered || sortComparator == null) return true
		if (localIndex > 0 && sortComparatorObj.compare(local[localIndex - 1], local[localIndex]) != -1) return false
		if (localIndex < local.lastIndex && sortComparatorObj.compare(local[localIndex], local[localIndex + 1]) != -1) return false
		return true
	}

	override fun subList(fromIndex: Int, toIndex: Int): List<E> {
		validate()
		return local.subList(fromIndex, toIndex).map { wrapped[it] }
	}

	override val size: Int
		get() {
			validate()
			return local.size
		}

	override fun contains(element: E): Boolean {
		validate()
		for (i in 0..lastIndex) {
			if (this[i] == element) return true
		}
		return false
	}

	override fun containsAll(elements: Collection<E>): Boolean {
		validate()
		for (element in elements) {
			if (!contains(element)) return false
		}
		return true
	}

	override fun get(index: Int): E {
		validate()
		return wrapped[local[index]]
	}

	override fun indexOf(element: E): Int {
		validate()
		if (local.isEmpty()) return -1
		// If the element doesn't pass the set filter, early out.
		if (filtered(element)) return -1

		for (i in 0..local.lastIndex) {
			if (element == local[i]) return i
		}
		return -1
	}

	override fun lastIndexOf(element: E): Int {
		validate()
		if (local.isEmpty()) return -1
		// If the element doesn't pass the set filter, early out.
		if (filtered(element)) return -1

		for (i in local.lastIndex downTo 0) {
			if (element == local[i]) return i
		}
		return -1
	}

	override fun isEmpty(): Boolean {
		validate()
		return local.isEmpty()
	}

	override fun iterator(): Iterator<E> {
		return ConcurrentListIteratorImpl(this)
	}

	override fun listIterator(): ListIterator<E> {
		return ConcurrentListIteratorImpl(this)
	}

	override fun listIterator(index: Int): ListIterator<E> {
		val iterator = ConcurrentListIteratorImpl(this)
		iterator.cursor = index
		return iterator
	}

	override fun concurrentIterator(): ConcurrentListIterator<E> {
		return ConcurrentListIteratorImpl(this)
	}

	inline fun iterate(body: (E) -> Boolean, reversed: Boolean) {
		if (reversed) iterateReversed(body)
		else iterate(body)
	}

	inline fun iterate(body: (E) -> Boolean) {
		val iterator = iteratorPool.obtain()
		while (iterator.hasNext()) {
			val shouldContinue = body(iterator.next())
			if (!shouldContinue) break
		}
		iteratorPool.free(iterator)
	}

	inline fun iterateReversed(body: (E) -> Boolean) {
		val iterator = iteratorPool.obtain()
		iterator.cursor = size
		while (iterator.hasPrevious()) {
			val shouldContinue = body(iterator.previous())
			if (!shouldContinue) break
		}
		iteratorPool.free(iterator)
	}

	//--------------------------------------------------------
	// Utility
	//--------------------------------------------------------

	/**
	 * Returns true if the element should be filtered out of the local list.
	 */
	private fun filtered(element: E): Boolean {
		return filter?.invoke(element) == false
	}

	/**
	 * Marks the list as dirty, causing the local index to be recreated on the next read.
	 * Calling this will dispatch a [reset] signal, even if this list was already dirty.
	 * @see validate
	 */
	fun dirty() {
		isDirty = true
		_reset.dispatch()
	}

	/**
	 * Recreates the local index.
	 * @see dirty
	 */
	fun validate() {
		if (!isDirty) return
		isDirty = false
		local.clear()
		val filter = filter
		for (i in 0..wrapped.lastIndex) {
			if (filter?.invoke(wrapped[i]) != false)
				local.add(i)
		}
		if (sortComparator != null)
			local.sortWith(sortComparatorObj)
	}

	/**
	 * Given a local index (ordered, reduced), returns the index of that element in the source list.
	 */
	fun localIndexToSource(localIndex: Int): Int {
		if (filter == null && sortComparator == null) return localIndex
		validate()
		return local[localIndex]
	}

	/**
	 * Given a source index, returns the index of that element in the ordered and reduced list.
	 */
	fun sourceIndexToLocal(sourceIndex: Int): Int {
		if (filter == null && sortComparator == null) return sourceIndex
		validate()
		val element = wrapped[sourceIndex]
		if (filtered(element)) return -1
		return if (sortComparator == null) {
			local.sortedInsertionIndex(sourceIndex, matchForwards = false)
		} else {
			local.sortedInsertionIndex(sourceIndex, matchForwards = false, comparator = insertionComparator)
		}
	}

	/**
	 * Returns the position the element at the wrapped [sourceIndex]
	 * @param sourceIndex The index of the element in the wrapped list to calculate where it should be inserted into
	 * the [local] list.
	 */
	private fun insertionIndex(sourceIndex: Int): Int {
		if (filter == null && sortComparator == null) return sourceIndex
		val element = wrapped[sourceIndex]
		if (filtered(element)) return -1
		return if (sortComparator == null) {
			local.sortedInsertionIndex(sourceIndex)
		} else {
			local.sortedInsertionIndex(sourceIndex, comparator = insertionComparator)
		}
	}

	override fun dispose() {
		unwatchWrappedList()
		_added.dispose()
		_removed.dispose()
		_changed.dispose()
		_modified.dispose()
		_reset.dispose()
	}

}

fun <E : Comparable<E>> ListView<E>.sort() {
	sortComparator = { o1, o2 -> o1.compareTo(o2) }
}

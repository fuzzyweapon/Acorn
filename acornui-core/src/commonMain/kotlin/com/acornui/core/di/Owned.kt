package com.acornui.core.di

import com.acornui.component.ComponentInit
import com.acornui.core.Disposable
import com.acornui.core.DisposedException
import com.acornui.core.Lifecycle
import com.acornui.signal.Signal
import com.acornui.signal.Signal1

/**
 * When an [Owned] object is created, the creator is its owner.
 * This is used for dependency injection, styling, and organization.
 */
interface Owned : Scoped {

	/**
	 * Returns true if this [Owned] object has been disposed.
	 */
	val isDisposed: Boolean

	/**
	 * Dispatched then this object has been disposed.
	 */
	val disposed: Signal<(Owned) -> Unit>

	/**
	 * The creator of this instance.
	 */
	val owner: Owned?

}

/**
 * Wraps the callback in an `if (!isDisposed)` block.
 */
inline fun Owned.notDisposed(crossinline callback: () -> Unit): () -> Unit {
	return { if (!isDisposed) callback() }
}

/**
 * Wraps the callback in an `if (!isDisposed)` block.
 */
inline fun <P1> Owned.notDisposed(crossinline callback: (P1) -> Unit): (P1) -> Unit {
	return { p1 -> if (!isDisposed) callback(p1) }
}

/**
 * Wraps the callback in an `if (!isDisposed)` block.
 */
inline fun <P1, P2> Owned.notDisposed(crossinline callback: (P1, P2) -> Unit): (P1, P2) -> Unit {
	return { p1, p2 -> if (!isDisposed) callback(p1, p2) }
}

/**
 * Wraps the callback in an `if (!isDisposed)` block.
 */
inline fun <P1, P2, P3> Owned.notDisposed(crossinline callback: (P1, P2, P3) -> Unit): (P1, P2, P3) -> Unit {
	return { p1, p2, p3 -> if (!isDisposed) callback(p1, p2, p3) }
}

/**
 * Wraps the callback in an `if (!isDisposed)` block.
 */
inline fun <P1, P2, P3, P4> Owned.notDisposed(crossinline callback: (P1, P2, P3, P4) -> Unit): (P1, P2, P3, P4) -> Unit {
	return { p1, p2, p3, p4 -> if (!isDisposed) callback(p1, p2, p3, p4) }
}

fun Owned.createScope(vararg dependenciesList: DependencyPair<*>, init: ComponentInit<Owned> = {}): Owned {
	return createScope(dependenciesList.toList(), init)
}

fun Owned.createScope(dependenciesList: List<DependencyPair<*>>, init: ComponentInit<Owned> = {}): Owned {
	val owner = this
	val o = object : Owned {
		override val isDisposed: Boolean = false
		override val disposed: Signal<(Owned) -> Unit> = owner.disposed
		override val owner: Owned? = owner
		override val injector: Injector = owner.injector + dependenciesList
	}
	o.init()
	return o
}

fun Owned.owns(other: Owned?): Boolean {
	var p: Owned? = other
	while (p != null) {
		if (p == this) return true
		p = p.owner
	}
	return false
}

/**
 * When this object is disposed, the target will also be disposed.
 */
fun <T : Disposable> Owned.own(target: T): T {
	val disposer: (Owned) -> Unit = { target.dispose() }
	disposed.add(disposer)
	if (target is Lifecycle) {
		target.disposed.add {
			disposed.remove(disposer)
		}
	}
	return target
}

/**
 * Factory methods for components typically don't have separated [owner] and [injector] parameters. This
 * implementation can be used to have a different dependency injector than what the owner uses.
 */
open class OwnedImpl(
		final override val owner: Owned?,
		final override val injector: Injector
) : Owned, Disposable {

	/**
	 * Constructs this OwnedImpl with no owner and the provided injector.
	 */
	constructor(injector: Injector) : this(null, injector)

	/**
	 * Constructs this OwnedImpl with the same injector as the owner.
	 */
	constructor(owner: Owned) : this(owner, owner.injector)

	private var _isDisposed = false
	override val isDisposed: Boolean
		get() = _isDisposed

	private val _disposed = Signal1<Owned>()
	override val disposed = _disposed.asRo()

	private val ownerDisposedHandler = {
		owner: Owned ->
		dispose()
	}

	init {
		owner?.disposed?.add(ownerDisposedHandler)
	}

	override fun dispose() {
		if (_isDisposed) throw DisposedException()
		_isDisposed = true
		owner?.disposed?.remove(ownerDisposedHandler)
		_disposed.dispatch(this)
		_disposed.dispose()
	}
}

/**
 * Traverses this Owned object's ownership lineage, invoking a callback on each owner up the chain.
 * (including this object)
 * @param callback The callback to invoke on each owner ancestor. If this callback returns true, iteration will
 * continue, if it returns false, iteration will be halted.
 * @return If [callback] returned false, this method returns the element on which the iteration halted.
 */
inline fun Owned.ownerWalk(callback: (Owned) -> Boolean): Owned? {
	var p: Owned? = this
	while (p != null) {
		val shouldContinue = callback(p)
		if (!shouldContinue) return p
		p = p.owner
	}
	return null
}
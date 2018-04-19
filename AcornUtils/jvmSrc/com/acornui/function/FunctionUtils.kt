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

package com.acornui.function

/**
 * Converts a method with zero parameters to a method with one (unused) parameter.
 */
val (() -> Unit).as1: FWrapper1
	get() = FWrapper1(this)

data class FWrapper1(private val inner: () -> Unit) : (Any?) -> Unit {
	override operator fun invoke(p1: Any?) {
		inner()
	}
}

/**
 * Converts a method with zero parameters to a method with two (unused) parameters.
 */
val (() -> Unit).as2: FWrapper2
	get() = FWrapper2(this)

data class FWrapper2(private val inner: () -> Unit) : (Any?, Any?) -> Unit {
	override operator fun invoke(p1: Any?, p2: Any?) {
		inner()
	}
}


/**
 * Converts a method with zero parameters to a method with three (unused) parameters.
 */
val (() -> Unit).as3: FWrapper3
	get() = FWrapper3(this)

data class FWrapper3(private val inner: () -> Unit) : (Any?, Any?, Any?) -> Unit {
	override operator fun invoke(p1: Any?, p2: Any?, p3: Any?) {
		inner()
	}
}

/**
 * Converts a method with zero parameters to a method with four (unused) parameters.
 */
val (() -> Unit).as4: FWrapper4
	get() = FWrapper4(this)

data class FWrapper4(private val inner: () -> Unit) : (Any?, Any?, Any?, Any?) -> Unit {
	override operator fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?) {
		inner()
	}
}

/**
 * Converts a method with zero parameters to a method with five (unused) parameters.
 */
val (() -> Unit).as5: FWrapper5
	get() = FWrapper5(this)

data class FWrapper5(private val inner: () -> Unit) : (Any?, Any?, Any?, Any?, Any?) -> Unit {
	override operator fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?) {
		inner()
	}
}

/**
 * Converts a method with zero parameters to a method with six (unused) parameters.
 */
val (() -> Unit).as6: FWrapper6
	get() = FWrapper6(this)

data class FWrapper6(private val inner: () -> Unit) : (Any?, Any?, Any?, Any?, Any?, Any?) -> Unit {
	override operator fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?) {
		inner()
	}
}
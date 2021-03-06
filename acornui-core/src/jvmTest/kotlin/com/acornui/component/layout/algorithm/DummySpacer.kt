package com.acornui.component.layout.algorithm

import com.acornui.component.layout.Spacer
import com.acornui.test.MockInjector

class DummySpacer(private val name: String,
				  initialSpacerWidth: Float = 0f,
				  initialSpacerHeight: Float = 0f
) : Spacer(MockInjector.owner, initialSpacerWidth, initialSpacerHeight) {

	override fun toString(): String {
		return name
	}
}
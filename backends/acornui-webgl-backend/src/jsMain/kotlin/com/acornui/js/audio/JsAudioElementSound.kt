package com.acornui.js.audio

import com.acornui.core.audio.AudioManager
import com.acornui.core.audio.Sound
import com.acornui.math.MathUtils
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event

class JsAudioElementSound(
		private val audioManager: AudioManager,
		private val src: String,
		override val priority: Float
) : Sound {

	override var onCompleted: (() -> Unit)? = null

	private var _isPlaying: Boolean = false
	override val isPlaying: Boolean
		get() = _isPlaying


	private var element: HTMLAudioElement

	private val elementEndedHandler = {
		event: Event ->
		if (!loop)
			complete()
		Unit
	}

	init {
		element = Audio(src)
		element.addEventListener("ended", elementEndedHandler)
		audioManager.registerSound(this)
	}

	private fun complete() {
		_isPlaying = false
		onCompleted?.invoke()
		onCompleted = null
		audioManager.unregisterSound(this)
	}

	override var loop: Boolean
		get() = element.loop
		set(value) {
			element.loop = value
		}

	private var _volume: Float = 1f
	override var volume: Float
		get() = _volume
		set(value) {
			_volume = value
			element.volume = MathUtils.clamp(value * audioManager.soundVolume, 0f, 1f).toDouble()
		}

	override fun setPosition(x: Float, y: Float, z: Float) {
	}

	override fun start() {
		if (_isPlaying) return
		_isPlaying = true
		element.play() // TODO: Delay and start time
	}

	override fun stop() {
		if (!_isPlaying) return
		_isPlaying = false
		element.pause() // TODO: Delay and start time
		element.currentTime = 0.0
	}

	override val currentTime: Float
		get() = element.currentTime.toFloat()

	override fun update() {
	}

	override fun dispose() {
		_isPlaying = false
		// Untested: http://stackoverflow.com/questions/3258587/how-to-properly-unload-destroy-a-video-element
		element.pause()
		element.src = ""
		element.load()
	}
}
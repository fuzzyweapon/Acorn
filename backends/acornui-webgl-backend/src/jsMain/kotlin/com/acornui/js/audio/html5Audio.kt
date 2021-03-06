package com.acornui.js.audio

import com.acornui.async.Deferred
import com.acornui.async.Promise
import org.khronos.webgl.ArrayBuffer
import org.w3c.dom.HTMLMediaElement
import org.w3c.dom.events.EventTarget


val audioContextSupported: Boolean = js("var JsAudioContext = window.AudioContext || window.webkitAudioContext; JsAudioContext != null") as Boolean

external class AudioContext : EventTarget {

	val currentTime: Float

	val destination: AudioDestinationNode

	val listener: AudioListener

	val sampleRate: Float

	/**
	 * suspended, running, closed
	 */
	val state: String

	fun close()

	fun createBuffer(numOfChannels: Int, length: Int, sampleRate: Int): AudioBuffer
	fun createBufferSource(): AudioBufferSourceNode
	fun createMediaElementSource(myMediaElement: HTMLMediaElement): MediaElementAudioSourceNode

	fun decodeAudioData(audioData: ArrayBuffer, callback: (decodedData: ArrayBuffer) -> Unit)

	fun createPanner(): PannerNode
	fun createGain(): GainNode
}

fun AudioContext.decodeAudioData(audioData: ArrayBuffer): Deferred<ArrayBuffer> {
	return object : Promise<ArrayBuffer>() {
		init {
			// The Audio Context handles creating source buffers from raw binary
			decodeAudioData(audioData, ::success)
		}
	}
}

external class AudioDestinationNode : AudioNode {

	var maxChannelCount: Int

}

external class MediaElementAudioSourceNode : AudioNode

external class AudioListener : AudioNode {
	fun setOrientation(x: Float, y: Float, z: Float, xUp: Float, yUp: Float, zUp: Float)
}

external class AudioBuffer

external class AudioBufferSourceNode : AudioNode {

	var buffer: ArrayBuffer


	var loop: Boolean
	var loopStart: Float
	var loopEnd: Float

	fun start()
	fun start(start: Float)
	fun start(start: Float, offset: Float)
	fun start(start: Float, offset: Float, duration: Float)

	fun stop(delay: Float)
}

abstract external class AudioNode : EventTarget {
	val context: AudioContext
	val numberOfInputs: Int
	val numberOfOutputs: Int
	var channelCount: Int
	fun connect(other: AudioNode)
	fun connect(other: AudioParam)

	fun disconnect()
}

external interface AudioParam {

	/**
	 * Gets or sets the current value of this AudioParam. Initially, the value is set to AudioParam.defaultValue.
	 * Part of the Web Audio API.
	 *
	 * Note: Though value can be set, any modifications happening while there are automation events scheduled—that is,
	 * events scheduled using the methods of the AudioParam—are ignored, without raising any exception.
	 */
	var value: Float

	val defaultValue: Float

	/**
	 * @param value A floating point number representing the value the AudioParam will change to at the given time.
	 * @param startTime A double representing the time (in seconds) after the AudioContext was first created that the
	 * change in value will happen. A TypeError is thrown if this value is negative.
	 */
	fun setValueAtTime(value: Float, startTime: Float)
	fun linearRampToValueAtTime(value: Float, endTime: Float)
	fun exponentialRampToValueAtTime(value: Float, endTime: Float)

	//....
}

external class GainNode : AudioNode {
	val gain: AudioParam
}

external class PannerNode : AudioNode {
	var panningModel: String

	var distanceModel: String

	var refDistance: Float
	var maxDistance: Float
	var rolloffFactor: Float
	var coneInnerAngle: Float
	var coneOuterAngle: Float
	var coneOuterGain: Float

	fun setPosition(x: Float, y: Float, z: Float)
	fun setOrientation(x: Float, y: Float, z: Float)
}

enum class PanningModel(val value: String) {
	EQUAL_POWER("equalpower"),

	// Sounds crackly to me when changing the position...
	HRTF("HRTF")
}
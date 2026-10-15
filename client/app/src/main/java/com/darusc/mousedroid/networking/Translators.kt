package com.darusc.mousedroid.networking

import android.util.Log
import com.darusc.mousedroid.layouts.KeyboardLayout
import com.darusc.mousedroid.layouts.Keycode
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.bluetooth.BatteryReport
import com.darusc.mousedroid.networking.bluetooth.HIDReport
import com.darusc.mousedroid.networking.bluetooth.KeyboardReport
import com.darusc.mousedroid.networking.bluetooth.MediaReport
import com.darusc.mousedroid.networking.bluetooth.MouseReport
import kotlin.experimental.and

/**
 * HID usage IDs used in translating the events into raw bytes for the HID protocol
 * are taken from the official usb specification
 *
 * https://www.usb.org/sites/default/files/documents/hut1_12v2.pdf
 */


/**
 * Socket events for the TCP/UDP communication
 */
private object RawSocketEvents {
    const val LCLICK: Byte = 0x01
    const val RCLICK: Byte = 0x02
    const val DOWN: Byte = 0x03
    const val UP: Byte = 0x04
    const val MOVE: Byte = 0x05
    const val SCROLL: Byte = 0x06
    const val KEYPRESS: Byte = 0x07
    const val SCROLL_H: Byte = 0x08
    const val ZOOM: Byte = 0x09
    const val MEDIA: Byte = 0x0A
    const val TEXT: Byte = 0x0B
}

private fun getMouseButtonHIDCode(button: InputEvent.MouseButton): Byte {
    return when (button) {
        InputEvent.MouseButton.NONE -> 0
        InputEvent.MouseButton.LEFT -> 1
        InputEvent.MouseButton.RIGHT -> 2
        InputEvent.MouseButton.MIDDLE -> 4
    }
}

/**
 * Transforms a media action into its corresponding bitmask.
 * The activated bit is the corresponding to the position
 * of the action in the descriptor listing
 */
private fun getMediaActionHIDBitmask(action: InputEvent.MediaAction): Short {
    return when(action) {
        InputEvent.MediaAction.FORWARD       -> 0b0000000000000001 // First in the descriptor listing
        InputEvent.MediaAction.REPLAY        -> 0b0000000000000010 // Second in the descriptor listing
        InputEvent.MediaAction.NEXT          -> 0b0000000000000100 // ...
        InputEvent.MediaAction.PREVIOUS      -> 0b0000000000001000
        InputEvent.MediaAction.PLAY_PAUSE    -> 0b0000000000010000
        InputEvent.MediaAction.VOLUME_MUTE   -> 0b0000000000100000
        InputEvent.MediaAction.VOLUME_UP     -> 0b0000000001000000
        InputEvent.MediaAction.VOLUME_DOWN   -> 0b0000000010000000
    }
}

/**
 * Translate the input event to 1 or more bluetooth HID reports
 * (e.g mouse click requires 2 reports -> one for pressing the button and one for releasing)
 */
fun InputEvent.toHIDReport(): Array<HIDReport> {
    Log.d("Mousedroid", this::class.java.toString())
    return when (this) {
        is InputEvent.MouseMove -> {
            val report = MouseReport(
                getMouseButtonHIDCode(this.button),
                (-this.dx).toByte(),
                (-this.dy).toByte(),
                0
            )
            arrayOf(report)
        }

        is InputEvent.MouseClick -> {
            val r1 = MouseReport(getMouseButtonHIDCode(this.button), 0, 0, 0)
            val r2 = MouseReport(0, 0, 0, 0)
            arrayOf(r1, r2)
        }

        is InputEvent.MouseDragState -> {
            val state = if (this.isDown) getMouseButtonHIDCode(this.button) else 0
            val report = MouseReport(state, 0, 0, 0)
            arrayOf(report)
        }

        is InputEvent.MouseScroll -> {
            val reports = arrayListOf<HIDReport>()

            val vScroll = (this.dy / 10).coerceIn(-127, 127).toByte()
            val hScroll = (this.dx / 10).coerceIn(-127, 127).toByte()

            if (vScroll.toInt() != 0) {
                reports.add(MouseReport(0, 0, 0, vScroll))
            } else if (hScroll.toInt() != 0) {
                reports.add(KeyboardReport(Keycode.MOD_LEFT_SHIFT, 0)) // Hold Shift
                reports.add(MouseReport(0, 0, 0, hScroll))              // Scroll
                reports.add(KeyboardReport(0, 0))                       // Release Shift
            }

            reports.toTypedArray()
        }

        is InputEvent.KeyPress -> {
            val reports = mutableListOf<KeyboardReport>()
            keyList.forEach {
                reports.add(KeyboardReport(it.modifier, it.code))
                reports.add(KeyboardReport(0, 0))
            }
            reports.toTypedArray()
        }

        is InputEvent.Zoom -> {
            // CTRL + mouse wheel rotation
            val zoom = this.scale.coerceIn(-127, 127).toByte()
            if (zoom.toInt() != 0) {
                arrayOf(
                    KeyboardReport(Keycode.MOD_LEFT_CTRL),   // Press CTRL
                    MouseReport(0, 0, 0, zoom),                 // Scroll
                    KeyboardReport(0)                                 // Release CTRL
                )
            } else {
                emptyArray()
            }
        }

        is InputEvent.NumpadKeyPress -> {
            arrayOf(
                KeyboardReport(0x00, this.key),
                KeyboardReport(0)
            )
        }

        is InputEvent.MediaEvent -> {
            val bitmask = getMediaActionHIDBitmask(this.action)
            arrayOf(MediaReport(bitmask), MediaReport(0))
        }

        is InputEvent.BatteryEvent -> {
            arrayOf(BatteryReport(this.percentage))
        }

        is InputEvent.TextInput -> {
            // Bluetooth HID reports are per-key and cannot carry arbitrary
            // text; voice typing is only supported over socket connections
            emptyArray()
        }
    }
}

private fun socketReport(vararg bytes: Byte): Array<ByteArray> {
    return arrayOf(bytes)
}

/**
 * Translate the input event to raw socket bytes
 */
fun InputEvent.toSocketReport(): Array<ByteArray> {
    return when (this) {
        is InputEvent.MouseMove -> {
            socketReport(RawSocketEvents.MOVE, this.dx.toByte(), this.dy.toByte())
        }

        is InputEvent.MouseScroll -> {
            if (this.dy != 0) {
                socketReport(RawSocketEvents.SCROLL, (this.dy).toByte())
            } else {
                socketReport(RawSocketEvents.SCROLL_H, (this.dx).toByte())
            }
        }

        is InputEvent.MouseClick -> {
            val code =
                if (this.button == InputEvent.MouseButton.LEFT) RawSocketEvents.LCLICK else RawSocketEvents.RCLICK
            socketReport(code)
        }

        is InputEvent.MouseDragState -> {
            val code = if (this.isDown) RawSocketEvents.DOWN else RawSocketEvents.UP
            socketReport(code)
        }

        is InputEvent.Zoom -> {
            socketReport(RawSocketEvents.ZOOM, this.scale.toByte())
        }

        is InputEvent.KeyPress -> {
            keyList.flatMap {
                listOf(
                    byteArrayOf(RawSocketEvents.KEYPRESS, it.code, it.modifier)
                )
            }.toTypedArray()
        }

        is InputEvent.NumpadKeyPress -> {
            socketReport(RawSocketEvents.KEYPRESS, this.key, 0x00)
        }

        is InputEvent.TextInput -> {
            // Frame: [TEXT][length_hi][length_lo][UTF-8 payload].
            // The server reads at most 255 bytes per packet, so longer text
            // is split into chunks (on UTF-8 character boundaries).
            val bytes = this.text.toByteArray(Charsets.UTF_8)
            val chunks = mutableListOf<ByteArray>()
            val maxChunk = 200
            var offset = 0
            while (offset < bytes.size) {
                var end = minOf(offset + maxChunk, bytes.size)
                // Back off to a UTF-8 character boundary: skip continuation
                // bytes, then exclude a trailing split lead byte as well
                while (end > offset && bytes[end - 1] and 0xC0.toByte() == 0x80.toByte()) {
                    end--
                }
                if (end > offset && bytes[end - 1] >= 0xC0.toByte()) {
                    end--
                }
                if (end <= offset) {
                    // Unreachable for valid UTF-8 (a character is at most
                    // 4 bytes); guards against an infinite loop
                    end = offset + 1
                }
                val chunk = bytes.copyOfRange(offset, end)
                val packet = ByteArray(3 + chunk.size)
                packet[0] = RawSocketEvents.TEXT
                packet[1] = (chunk.size shr 8).toByte()
                packet[2] = chunk.size.toByte()
                chunk.copyInto(packet, 3)
                chunks.add(packet)
                offset = end
            }
            chunks.toTypedArray()
        }

        is InputEvent.MediaEvent -> {
            val bitmask = getMediaActionHIDBitmask(this.action)
            socketReport(RawSocketEvents.MEDIA, (bitmask and 0xFF).toByte())
        }

        is InputEvent.BatteryEvent -> { socketReport() }
    }
}
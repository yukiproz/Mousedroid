package com.darusc.mousedroid.mkinput

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/**
 * EditText that can intercept text committed by the IME (e.g. voice typing)
 * and forward it to [onTextCommitted] instead of inserting it into the field.
 *
 * Intermediate composing text (voice-typing partials) is passed through to the
 * field; the regular [android.text.TextWatcher] must be disabled while
 * [interceptCommits] is true so partials are not sent as keypresses.
 */
class VoiceTypingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    /**
     * When true, committed text is delivered to [onTextCommitted] and never
     * inserted into the field.
     */
    var interceptCommits: Boolean = false

    var onTextCommitted: ((String) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        if (!interceptCommits) {
            return connection
        }
        return object : InputConnectionWrapper(connection, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val committed = text?.toString().orEmpty()
                if (committed.isNotEmpty()) {
                    deliver(committed)
                }
                // Swallow: keep the field empty so committed text is only
                // ever sent through onTextCommitted
                return true
            }

            override fun finishComposingText(): Boolean {
                // Some IMEs never call commitText and only use composing
                // text; deliver whatever is in the field in that case
                val composed = this@VoiceTypingEditText.text?.toString().orEmpty()
                if (composed.isNotEmpty()) {
                    deliver(composed)
                }
                return true
            }
        }
    }

    private fun deliver(text: String) {
        // Clear first so no text change can be observed afterwards
        setText("")
        onTextCommitted?.invoke(text)
    }
}

package li.songe.gkd.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import li.songe.gkd.app

object KeyboardUtils {
    fun hideSoftInput(activity: Activity) {
        hideSoftInput(activity.window)
    }

    fun hideSoftInput(window: Window) {
        val tempTag = "keyboardTagView"
        var view = window.currentFocus
        if (view == null) {
            val decorView = window.decorView
            val focusView = decorView.findViewWithTag<View?>(tempTag)
            if (focusView == null) {
                view = EditText(window.context)
                view.tag = tempTag
                (decorView as ViewGroup).addView(view, 0, 0)
            } else {
                view = focusView
            }
            view.requestFocus()
        }
        hideSoftInput(view)
    }

    fun hideSoftInput(view: View) {
        app.inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

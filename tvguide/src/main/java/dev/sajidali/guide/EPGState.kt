package dev.sajidali.guide

import android.os.Parcelable
import android.view.View

/**
 * Created by MVRM on 19/04/2017.
 */

class EPGState(superState: Parcelable) : View.BaseSavedState(superState) {

    var currentEvent: Int = -1
}

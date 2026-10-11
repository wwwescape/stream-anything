package com.wwwescape.streamanything.util

import android.content.Context
import android.content.res.Configuration

/** Android TV / Google TV — D-pad first, no touch, and the Play on TV receiver. */
fun Context.isTelevision(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

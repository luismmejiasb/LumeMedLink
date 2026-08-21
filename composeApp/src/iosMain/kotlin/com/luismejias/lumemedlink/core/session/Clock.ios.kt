package com.luismejias.lumemedlink.core.session

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun epochMillisNow(): Long = (NSDate().timeIntervalSince1970() * 1000).toLong()

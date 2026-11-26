package app.bloblog

enum class Notice { NEED_BT_PERMISSION, BT_OFF, LISTEN_FAILED, WHOSE, HC_NOT_CONNECTED }

/** The one top-strip notice to show, in the priority order of spec §5.1. */
fun noticeFor(scan: Scanner.State, unassigned: Int, hcAvailable: Boolean, hcConnected: Boolean, ownerTab: Boolean): Notice? = when {
    scan == Scanner.State.NO_PERMISSION -> Notice.NEED_BT_PERMISSION
    scan == Scanner.State.BLUETOOTH_OFF -> Notice.BT_OFF
    scan == Scanner.State.FAILED -> Notice.LISTEN_FAILED
    unassigned > 0 -> Notice.WHOSE
    ownerTab && hcAvailable && !hcConnected -> Notice.HC_NOT_CONNECTED
    else -> null
}

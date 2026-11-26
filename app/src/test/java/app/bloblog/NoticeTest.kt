package app.bloblog

import app.bloblog.Scanner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeTest {
    private fun n(scan: State = State.LISTENING, unassigned: Int = 0, avail: Boolean = true, connected: Boolean = true, owner: Boolean = true) =
        noticeFor(scan, unassigned, avail, connected, owner)

    @Test fun needPermissionFirst() = assertEquals(Notice.NEED_BT_PERMISSION, n(State.NO_PERMISSION, 2, connected = false))
    @Test fun bluetoothOff() = assertEquals(Notice.BT_OFF, n(State.BLUETOOTH_OFF, 2))
    @Test fun listenFailed() = assertEquals(Notice.LISTEN_FAILED, n(State.FAILED, 2))
    @Test fun whose() = assertEquals(Notice.WHOSE, n(unassigned = 1))
    @Test fun hcNotConnected() = assertEquals(Notice.HC_NOT_CONNECTED, n(connected = false))
    @Test fun nothing() = assertNull(n())
    @Test fun whoseBeatsHc() = assertEquals(Notice.WHOSE, n(unassigned = 1, connected = false))
    @Test fun hcOnlyOnOwnerTab() = assertNull(n(connected = false, owner = false))
    @Test fun hcUnavailableNeverShows() = assertNull(n(avail = false, connected = false))
}

package dev.tara.permissions

import dev.tara.domain.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionClassificationTest {
    @Test fun `first use is denied instead of permanently denied`() {
        assertEquals(PermissionStatus.DENIED, classifyPermission(true, false, false, false, false))
    }
    @Test fun `ordinary denial remains requestable`() {
        assertEquals(PermissionStatus.DENIED, classifyPermission(true, false, false, true, true))
    }
    @Test fun `completed denial without rationale needs settings`() {
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, classifyPermission(true, false, false, true, false))
    }
    @Test fun `revoked grant is detected before a new prompt response`() {
        assertEquals(PermissionStatus.REVOKED, classifyPermission(true, false, true, false, false))
    }
    @Test fun `permanent denial after revocation needs settings rather than another prompt`() {
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, classifyPermission(true, false, true, true, false))
    }
    @Test fun `current grant wins over prior denial`() {
        assertEquals(PermissionStatus.GRANTED, classifyPermission(true, true, false, true, false))
    }
    @Test fun `missing hardware is unavailable`() {
        assertEquals(PermissionStatus.UNAVAILABLE, classifyPermission(false, false, false, false, null))
    }
}

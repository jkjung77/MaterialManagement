package kr.baraplt.material.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun debugBuildSkipsPlayCheck() {
        assertFalse(UpdatePolicy.shouldCheck(debuggable = true))
    }

    @Test
    fun releaseBuildChecksPlay() {
        assertTrue(UpdatePolicy.shouldCheck(debuggable = false))
    }

    @Test
    fun prefersFlexibleWhenBothAllowed() {
        assertEquals(
            UpdatePolicy.Path.FLEXIBLE,
            UpdatePolicy.path(flexibleAllowed = true, immediateAllowed = true)
        )
    }

    @Test
    fun usesImmediateWhenFlexibleUnavailable() {
        assertEquals(
            UpdatePolicy.Path.IMMEDIATE,
            UpdatePolicy.path(flexibleAllowed = false, immediateAllowed = true)
        )
    }

    @Test
    fun opensStoreWhenPlayFlowUnavailable() {
        assertEquals(
            UpdatePolicy.Path.STORE,
            UpdatePolicy.path(flexibleAllowed = false, immediateAllowed = false)
        )
    }
}

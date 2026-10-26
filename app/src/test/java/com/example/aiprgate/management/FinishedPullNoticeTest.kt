package com.example.aiprgate.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishedPullNoticeTest {
    @Test
    fun mergedPullReportsCompletionInsteadOfBlockers() {
        val merged = testPull().copy(state = "closed", merged = true, mergeable = null)

        assertEquals("병합 완료", finishedPullNotice(merged)?.first)
    }

    @Test
    fun closedUnmergedPullIsReportedAsClosed() {
        val closed = testPull().copy(state = "closed", merged = false)

        assertEquals("병합 없이 종료된 PR", finishedPullNotice(closed)?.first)
    }

    @Test
    fun openPullKeepsTheNormalMergeForm() {
        assertNull(finishedPullNotice(testPull()))
    }
}

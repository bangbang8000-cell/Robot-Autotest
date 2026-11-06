package com.qa.autotest.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportStateMachineTest {

    @Test
    void draftOnlyFreezes() {
        assertTrue(ReportState.DRAFT.canTransitionTo(ReportState.FROZEN));
        assertFalse(ReportState.DRAFT.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.DRAFT.canTransitionTo(ReportState.DRAFT));
        assertFalse(ReportState.DRAFT.canTransitionTo(null));
    }

    @Test
    void frozenCanRevokeOrArchiveButNeverGoBack() {
        assertTrue(ReportState.FROZEN.canTransitionTo(ReportState.REVOKED));
        assertTrue(ReportState.FROZEN.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.FROZEN.canTransitionTo(ReportState.DRAFT));
        assertFalse(ReportState.ARCHIVED.canTransitionTo(ReportState.FROZEN));
    }

    @Test
    void revokedCanOnlyArchive() {
        assertTrue(ReportState.REVOKED.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.REVOKED.canTransitionTo(ReportState.FROZEN));
    }

    @Test
    void parseIsCaseInsensitiveAndRejectsBlank() {
        assertEquals(ReportState.FROZEN, ReportState.parse(" frozen "));
        assertThrows(IllegalArgumentException.class, () -> ReportState.parse("  "));
    }
}

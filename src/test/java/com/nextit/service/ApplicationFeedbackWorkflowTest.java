package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.Application;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationFeedbackWorkflowTest {

    @Test
    void applicationModelMapsRemarksAndReviewedAt() {
        Application app = new Application();
        app.setApplicationId(101);
        app.setOpportunityId(202);
        app.setStudentId(303);
        app.setStatus("Pending");
        app.setRemarks("Great candidate");
        LocalDateTime now = LocalDateTime.now();
        app.setReviewedAt(now);

        assertEquals(101, app.getApplicationId());
        assertEquals(202, app.getOpportunityId());
        assertEquals(303, app.getStudentId());
        assertEquals("Pending", app.getStatus());
        assertEquals("Great candidate", app.getRemarks());
        assertEquals(now, app.getReviewedAt());
    }

    @Test
    void applicationAllowsNullableRemarksAndReviewedAt() {
        Application app = new Application();
        app.setRemarks(null);
        app.setReviewedAt(null);

        assertNull(app.getRemarks());
        assertNull(app.getReviewedAt());
    }
}

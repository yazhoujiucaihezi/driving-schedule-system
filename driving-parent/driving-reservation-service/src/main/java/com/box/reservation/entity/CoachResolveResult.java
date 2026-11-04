package com.box.reservation.entity;

import com.box.api.coach.CoachDTO;

import java.util.List;

public record CoachResolveResult(
        Status status,               // RESOLVED / AMBIGUOUS / NOT_FOUND
        CoachDTO coach,              // RESOLVED 时非空
        List<String> candidates      // AMBIGUOUS 时非空，给 LLM 问用户用
) {
    public enum Status { RESOLVED, AMBIGUOUS, NOT_FOUND }
}
package com.nextit.model;

public enum VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED,
    SUSPENDED;

    public String displayName() {
        return switch (this) {
            case UNVERIFIED -> "Unverified";
            case PENDING -> "Pending Verification";
            case VERIFIED -> "Verified";
            case REJECTED -> "Verification Rejected";
            case SUSPENDED -> "Account Suspended";
        };
    }
}

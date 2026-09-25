package dev.fieldservice.reindex;

public record WorkOrder(
        String workOrderId,
        String photoUrl,
        String photoCaption,
        DispatchStatus dispatchStatus,
        String technicianFollowUp) {

    public enum DispatchStatus {
        NEW, DISPATCHED, AWAITING_FOLLOW_UP, CLOSED, CANCELLED
    }

    public boolean belongsInSearchIndex() {
        return dispatchStatus == DispatchStatus.DISPATCHED
                || dispatchStatus == DispatchStatus.AWAITING_FOLLOW_UP;
    }

    public String searchableText() {
        return "Work order " + workOrderId
                + ". Photo: " + photoCaption
                + ". Dispatch: " + dispatchStatus
                + ". Technician follow-up: " + technicianFollowUp;
    }
}

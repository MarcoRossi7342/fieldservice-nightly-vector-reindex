package dev.fieldservice.reindex;

import org.junit.jupiter.api.Test;

import static dev.fieldservice.reindex.WorkOrder.DispatchStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderTest {
    @Test
    void onlyActiveDispatchWorkEntersTheSearchIndex() {
        WorkOrder dispatched = order("WO-2041", DISPATCHED);
        WorkOrder waiting = order("WO-2042", AWAITING_FOLLOW_UP);
        WorkOrder closed = order("WO-2043", CLOSED);

        assertThat(dispatched.belongsInSearchIndex()).isTrue();
        assertThat(waiting.belongsInSearchIndex()).isTrue();
        assertThat(closed.belongsInSearchIndex()).isFalse();
        assertThat(dispatched.searchableText()).contains("meter cabinet", "DISPATCHED", "seal number");
    }

    private WorkOrder order(String id, WorkOrder.DispatchStatus status) {
        return new WorkOrder(id, "https://dispatch.example/photos/" + id,
                "meter cabinet and tamper seal", status, "record the replacement seal number");
    }
}

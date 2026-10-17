package com.jm.inventory.domain.cyclecount;

import java.util.UUID;
import lombok.Value;

/** {@code countedQuantity} is {@code null} until someone records a count for this line. */
@Value
public class CycleCountLine {
    UUID id;
    UUID productId;
    Integer countedQuantity;
}

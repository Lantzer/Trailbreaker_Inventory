package com.TbInventory.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request object for updating tank properties.
 * Use builder pattern to specify only the fields you want to update.
 *
 * Example usage:
 * - Update label only: TankUpdateRequest.builder().newLabel("FV-1A").build()
 * - Update capacity only: TankUpdateRequest.builder().newCapacity(new BigDecimal("150")).newCapacityUnitId(1).build()
 * - Update both: TankUpdateRequest.builder().newLabel("FV-1A").newCapacity(new BigDecimal("150")).newCapacityUnitId(1).build()
 */
@Data
@Builder
public class TankUpdateRequest {
    private String newLabel;
    private BigDecimal newCapacity;
    private Integer newCapacityUnitId;

    public boolean hasLabelUpdate() {
        return newLabel != null;
    }

    public boolean hasCapacityUpdate() {
        return newCapacity != null && newCapacityUnitId != null;
    }
}

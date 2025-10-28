package com.TbInventory.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for transaction requests from the frontend.
 * Also used for starting new batches (use tankId + newBatchLabel instead of batchId).
 */
@Data
public class TransactionRequest {
    // For transactions on existing batches
    private Integer batchId;

    // For starting new batches (mutually exclusive with batchId)
    private Integer tankId;

    private Integer transactionTypeId;
    private BigDecimal quantity;
    private Integer quantityUnitId;
    private String notes;

    // For fermenter-to-fermenter transfers
    private String destinationTankLabel;

    // For starting new batches
    private String newBatchLabel;
}

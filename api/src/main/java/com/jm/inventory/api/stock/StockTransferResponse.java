package com.jm.inventory.api.stock;

import com.jm.inventory.application.stock.StockMovementRecordingService.StockTransferResult;

public record StockTransferResponse(StockMovementResponse outboundMovement, StockMovementResponse inboundMovement) {

    public static StockTransferResponse from(StockTransferResult result) {
        return new StockTransferResponse(
                StockMovementResponse.from(result.outboundMovement()),
                StockMovementResponse.from(result.inboundMovement()));
    }
}

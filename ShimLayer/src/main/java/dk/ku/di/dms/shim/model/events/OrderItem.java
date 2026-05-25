package dk.ku.di.dms.shim.model.events;

public record OrderItem(
        int productId,
        int quantity,
        float unitPrice
) {}
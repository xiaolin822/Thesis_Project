package dk.ku.di.dms.shim.model.events;

import java.util.List;

public record InvoiceIssued(
        CustomerCheckout customer,
        int orderId,
        String invoiceNumber,
        String issueDate,   // ⚠️ ISO string
        float totalInvoice,
        List<OrderItem> items,
        String instanceId
) {}
package dk.ku.di.dms.shim.model.events;

public record CustomerCheckout(
        int customerId,
        String firstName,
        String lastName
) {}
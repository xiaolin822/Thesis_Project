package dk.ku.di.dms.vms.marketplace.payment;

import dk.ku.di.dms.vms.marketplace.common.entities.OrderItem;
import dk.ku.di.dms.vms.marketplace.common.events.InvoiceIssued;
import dk.ku.di.dms.vms.marketplace.common.events.PaymentConfirmed;
import dk.ku.di.dms.vms.marketplace.payment.entities.OrderPayment;
import dk.ku.di.dms.vms.marketplace.payment.entities.OrderPaymentCard;
import dk.ku.di.dms.vms.marketplace.payment.enums.PaymentStatus;
import dk.ku.di.dms.vms.marketplace.payment.enums.PaymentType;
import dk.ku.di.dms.vms.marketplace.payment.integration.CardOptions;
import dk.ku.di.dms.vms.marketplace.payment.integration.PaymentIntent;
import dk.ku.di.dms.vms.marketplace.payment.integration.PaymentIntentCreateOptions;
import dk.ku.di.dms.vms.marketplace.payment.provider.IExternalProvider;
import dk.ku.di.dms.vms.marketplace.payment.repositories.IOrderPaymentCardRepository;
import dk.ku.di.dms.vms.marketplace.payment.repositories.IOrderPaymentRepository;
import dk.ku.di.dms.vms.modb.api.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import static dk.ku.di.dms.vms.marketplace.common.Constants.*;
import static dk.ku.di.dms.vms.modb.api.enums.TransactionTypeEnum.W;
import static java.lang.System.Logger.Level.DEBUG;

@Microservice("payment")
public final class PaymentService {

    private static final System.Logger LOGGER = System.getLogger(PaymentService.class.getName());

    private static final boolean provider;
    private static final IExternalProvider externalProvider;

    static {
        boolean provider_aux = false;
        IExternalProvider ext_provider_aux = null;
        InputStream input = PaymentService.class.getClassLoader().getResourceAsStream("app.properties");
        Properties prop = new Properties();
        if (input != null) {
            try {
                prop.load(input);
                String str = prop.getProperty("provider");
                if (str != null) {
                    provider_aux = str.contentEquals("true");
                }
            } catch (IOException ignored) { }
        }
        provider = provider_aux;
        externalProvider = ext_provider_aux;
    }

    private final IOrderPaymentRepository orderPaymentRepository;
    private final IOrderPaymentCardRepository orderPaymentCardRepository;

    public PaymentService(IOrderPaymentRepository orderPaymentRepository,
                          IOrderPaymentCardRepository orderPaymentCardRepository){
        this.orderPaymentRepository = orderPaymentRepository;
        this.orderPaymentCardRepository = orderPaymentCardRepository;
    }

    @Inbound(values = {INVOICE_ISSUED})
    @Outbound(PAYMENT_CONFIRMED)
    @Transactional(type=W)
    @Parallel
    public PaymentConfirmed processPayment(InvoiceIssued invoiceIssued) {
        Date now = new Date();

        if (invoiceIssued == null || invoiceIssued.customer == null) {
            return new PaymentConfirmed(null, 0, 0, null, now, invoiceIssued != null ? invoiceIssued.instanceId : "0");
        }

        LOGGER.log(DEBUG, "APP: Payment received an invoice issued event with TID: "+ invoiceIssued.instanceId);
        PaymentStatus status = getPaymentStatus(invoiceIssued);
        int seq = 1;

        String rawPaymentType = invoiceIssued.customer.PaymentType;
        boolean isCard = isCard(rawPaymentType);

        if (isCard) {
            boolean isCreditCard = "CREDIT_CARD".equalsIgnoreCase(rawPaymentType);
            OrderPayment cardPaymentLine = new OrderPayment(
                    invoiceIssued.customer.CustomerId,
                    invoiceIssued.orderId,
                    seq,
                    isCreditCard ? PaymentType.CREDIT_CARD : PaymentType.DEBIT_CARD,
                    invoiceIssued.customer.Installments,
                    invoiceIssued.totalInvoice,
                    status
            );
            this.orderPaymentRepository.insert(cardPaymentLine);
            OrderPaymentCard card = new OrderPaymentCard(
                    invoiceIssued.customer.CustomerId,
                    invoiceIssued.orderId,
                    seq,
                    invoiceIssued.customer.CardNumber,
                    invoiceIssued.customer.CardHolderName,
                    invoiceIssued.customer.CardExpiration,
                    invoiceIssued.customer.CardBrand
            );
            this.orderPaymentCardRepository.insert(card);
            seq++;
        } else if (rawPaymentType != null && "BOLETO".equalsIgnoreCase(rawPaymentType)) {

            OrderPayment paymentSlip = new OrderPayment(
                    invoiceIssued.customer.CustomerId,
                    invoiceIssued.orderId,
                    seq,
                    PaymentType.BOLETO,
                    1,
                    invoiceIssued.totalInvoice,
                    status
            );
            this.orderPaymentRepository.insert(paymentSlip);
            seq++;
        }

        // 🌟 终极防御：判定 items 列表是否完整，防止高并发导致循环 NPE
        if (status == PaymentStatus.succeeded && invoiceIssued.items != null) {
            for (OrderItem item : invoiceIssued.items) {
                if (item != null && item.total_incentive > 0) {
                    OrderPayment voucher = new OrderPayment(
                            invoiceIssued.customer.CustomerId,
                            invoiceIssued.orderId,
                            seq,
                            PaymentType.VOUCHER,
                            1,
                            item.total_incentive,
                            status
                    );
                    this.orderPaymentRepository.insert(voucher);
                    seq++;
                }
            }
        }

        return new PaymentConfirmed(invoiceIssued.customer, invoiceIssued.orderId, invoiceIssued.totalInvoice, invoiceIssued.items, now, invoiceIssued.instanceId);
    }

    private static boolean isCard(String type){
        if (type == null) {
            return false;
        }
        String upperType = type.trim().toUpperCase();
        return "CREDIT_CARD".equals(upperType) || "DEBIT_CARD".equals(upperType);
    }

    private static PaymentStatus getPaymentStatus(InvoiceIssued invoiceIssued) {
        PaymentStatus status;
        if (provider && externalProvider != null && invoiceIssued != null && invoiceIssued.customer != null) {
            try {
                PaymentIntent intent = externalProvider.Create(new PaymentIntentCreateOptions(
                        invoiceIssued.customer.CardHolderName,
                        invoiceIssued.totalInvoice,
                        invoiceIssued.customer.PaymentType,
                        invoiceIssued.invoiceNumber,
                        new CardOptions(invoiceIssued.customer.CardNumber, invoiceIssued.customer.CardExpiration,
                                invoiceIssued.customer.CardExpiration, invoiceIssued.customer.CardSecurityNumber),
                        "off_session",
                        "USD"));
                status = (intent != null && intent.status != null && intent.status.contentEquals("succeeded"))
                        ? PaymentStatus.succeeded : PaymentStatus.requires_payment_method;
            } catch (Exception e) {
                status = PaymentStatus.succeeded;
            }
        } else {
            status = PaymentStatus.succeeded;
        }
        return status;
    }

}
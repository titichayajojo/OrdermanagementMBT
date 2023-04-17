package th.ac.kmitl.se;

import org.graphwalker.core.machine.ExecutionContext;
import org.graphwalker.java.annotation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.*;
import static org.mockito.Mockito.*;

// Update the filename of the saved file of your model here.
@Model(file = "model.json")
public class OrderAdapter extends ExecutionContext {
    // The following method add some delay between each step
    // so that we can see the progress in GraphWalker player.
    public static int delay = 300;

    @AfterElement
    public void afterEachStep() {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    OrderDB orderDB;
    ProductDB productDB;
    PaymentService paymentService;
    ShippingService shippingService;
    Order order;

    Card makeUpCard;
    Address johnMakeUpAddress;

    ArgumentCaptor<PaymentCallback> callbackCaptor;
    ArgumentCaptor<PaymentCallback> callbackCaptorRefund;

    static int orderId = 1;
    static int paymentServiceRefundCount = 1;
    static int paymentServiceCountPay = 1;

    @BeforeExecution
    public void setUp() {
        // Add the code here to be executed before
        // GraphWalk starts traversing the model.
        orderDB = mock(OrderDB.class);
        productDB = mock(ProductDB.class);
        paymentService = mock(PaymentService.class);
        shippingService = mock(ShippingService.class);
        order = new Order(orderDB, productDB, paymentService, shippingService);

        makeUpCard = new Card("1234567890123456", "John", 1000, 1003);
        johnMakeUpAddress = new Address("Home", "123", "My Street", "New Town", "New City", "12345");

        callbackCaptor = ArgumentCaptor.forClass(PaymentCallback.class);
        callbackCaptorRefund = ArgumentCaptor.forClass(PaymentCallback.class);

    }

    @Edge()
    public void reset() {
        System.out.println("Edge reset");

        order = new Order(orderDB, productDB, paymentService, shippingService);
        Assertions.assertEquals(Order.Status.CREATED, order.getStatus());
    }

    @Edge()
    public void place() {
        System.out.println("Edge place");

        when(orderDB.getOrderID()).thenReturn(orderId++);
        order.place("John", "Apple Watch", 2, johnMakeUpAddress);
        // status of the order is “PLACED”
        Assertions.assertEquals(Order.Status.PLACED, order.getStatus());
    }

    @Edge()
    public void cancel() {
        System.out.println("Edge cancel");
        order.cancel();
    }

    @Edge()
    public void pay() {
        System.out.println("Edge pay");

        when(productDB.getPrice("Apple Watch")).thenReturn(1500f);
        when(productDB.getWeight("Apple Watch")).thenReturn(350f);
        when(shippingService.getPrice(johnMakeUpAddress, 700f)).thenReturn(50f);
        // the requested amount to be paid is 3050;
        assertEquals(order.getTotalCost(), 3050F);

        order.pay(makeUpCard);
        Assertions.assertEquals(Order.Status.PAYMENT_CHECK, order.getStatus());
    }

    @Edge()
    public void retryPay() {
        System.out.println("Edge retryPay");

        // retry payment
        order.pay(makeUpCard);
        // the status of the order is changed to “PAYMENT_CHECK”;
        assertEquals(Order.Status.PAYMENT_CHECK, order.getStatus());
    }

    @Edge()
    public void paySuccess() {
        System.out.println("Edge paySuccess");
        // After the payment service has called back with a success,
        verify(paymentService, times(paymentServiceCountPay++)).pay(any(Card.class), anyFloat(),
                callbackCaptor.capture());
        callbackCaptor.getValue().onSuccess("1");

        // the status of the order is changed to “PAID”;
        assertEquals(Order.Status.PAID, order.getStatus());
        // the payment confirmation code has been recorded in the order;
        assertEquals(order.paymentConfirmCode, "1");
    }

    @Edge()
    public void payError() {
        System.out.println("Edge payError");
        // After the payment service has called back with an error,
        verify(paymentService, times(paymentServiceCountPay++)).pay(any(Card.class), anyFloat(),
                callbackCaptor.capture());
        callbackCaptor.getValue().onError("0");
        // the status of the order is changed to “PAYMENT_ERROR”
        assertEquals(Order.Status.PAYMENT_ERROR, order.getStatus());
    }

    @Edge()
    public void ship() {
        System.out.println("Edge ship");

        when(shippingService.ship(johnMakeUpAddress, 700F)).thenReturn("11001");
        order.ship();
        // ■ the tracking code has been recorded in the order;
        assertEquals(order.trackingCode, "11001");
        // ■ the status of the order is changed to “SHIPPED”;
        assertEquals(Order.Status.SHIPPED, order.getStatus());
    }

    @Edge()
    public void refundSuccess() {
        System.out.println("Edge refundSuccess");
        // the status of the order is changed to “AWAIT_REFUND”;
        assertEquals(Order.Status.AWAIT_REFUND, order.getStatus());

        // refund payment service
        verify(paymentService, times(paymentServiceRefundCount++)).refund(any(), callbackCaptorRefund.capture());
        callbackCaptorRefund.getValue().onSuccess("1");

        // the status of the order is changed to “REFUNDED”;
        assertEquals(Order.Status.REFUNDED, order.getStatus());
    }

    @Edge()
    public void refundError() {
        System.out.println("Edge refundError");
        // the status of the order is changed to “AWAIT_REFUND”;
        assertEquals(Order.Status.AWAIT_REFUND, order.getStatus());

        // refund payment service
        verify(paymentService, times(paymentServiceRefundCount++)).refund(any(), callbackCaptorRefund.capture());
        callbackCaptorRefund.getValue().onError("0");

        // The status of the order is changed to “REFUND_ERROR”
        assertEquals(Order.Status.REFUND_ERROR, order.getStatus());
    }
}

package example;

public class OrderService {
    private static final double TAX_RATE = 0.19;

    public double calculateTotal(double price, int quantity) {
        double subtotal = price * quantity;
        double tax = subtotal * TAX_RATE;
        double total = subtotal + tax;

        if (quantity >= 10) {
            total = total * 0.95;
        }

        return total;
    }
}

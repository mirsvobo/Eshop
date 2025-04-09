package org.example.eshop.service;

import org.example.eshop.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service("dummyShippingService")
public class DummyShippingService implements ShippingService {

    private static final Logger log = LoggerFactory.getLogger(DummyShippingService.class);
    private static final BigDecimal FIXED_SHIPPING_COST_CZK = new BigDecimal("150.00");
    private static final BigDecimal FIXED_SHIPPING_COST_EUR = new BigDecimal("7.00");
    private static final BigDecimal SHIPPING_TAX_RATE = new BigDecimal("0.21");

    @Override
    public BigDecimal calculateShippingCost(Order order, String currency) {
        log.info("Calculating dummy shipping cost for order: {} in currency: {}", order.getOrderCode(), currency);
        // TODO: Implementovat volání skutečného API pro výpočet dopravy (fix + km)
        if ("EUR".equals(currency)) {
            log.debug("Returning dummy shipping cost: {} EUR", FIXED_SHIPPING_COST_EUR);
            return FIXED_SHIPPING_COST_EUR;
        } else {
            log.debug("Returning dummy shipping cost: {} CZK", FIXED_SHIPPING_COST_CZK);
            return FIXED_SHIPPING_COST_CZK;
        }
    }

    @Override
    public BigDecimal getShippingTaxRate() {
        return SHIPPING_TAX_RATE;
    }
}
package br.com.fiap.postech.carworkshop.billing.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetItemTest {

    @Test
    void multipliesUnitPriceByQuantity() {
        BudgetItem item = new BudgetItem(BudgetItem.PART, 7L, "Filtro", 3, new BigDecimal("35.90"));

        assertEquals(new BigDecimal("107.70"), item.subtotal());
    }
}

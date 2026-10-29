package br.com.fiap.postech.carworkshop.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class BudgetItemEmbeddable {

    @Column(name = "type", nullable = false, length = 10)
    public String type;

    @Column(name = "catalog_id", nullable = false)
    public Long catalogId;

    @Column(name = "description", nullable = false)
    public String description;

    @Column(name = "quantity", nullable = false)
    public int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    public BigDecimal unitPrice;
}

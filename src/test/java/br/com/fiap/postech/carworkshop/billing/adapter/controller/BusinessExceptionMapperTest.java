package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.presenter.ErrorResponse;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotApprovableException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BusinessException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessExceptionMapperTest {

    private final BusinessExceptionMapper mapper = new BusinessExceptionMapper();

    @Test
    void mapsEachBusinessFailureToItsStatus() {
        assertEquals(404, BusinessExceptionMapper.statusOf(new BudgetNotFoundException("no budget")));
        assertEquals(403, BusinessExceptionMapper.statusOf(new NotBudgetOwnerException("not yours")));
        assertEquals(409, BusinessExceptionMapper.statusOf(
                new BudgetNotApprovableException("deadline is over")));
        assertEquals(400, BusinessExceptionMapper.statusOf(new BusinessException("bad request")));
    }

    @Test
    void answersWithTheStatusAndTheMessageInTheBody() {
        Response response = mapper.toResponse(new NotBudgetOwnerException("not yours"));

        assertEquals(403, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(403, body.status());
        assertEquals("not yours", body.error());
        assertNotNull(body.timestamp());
    }
}

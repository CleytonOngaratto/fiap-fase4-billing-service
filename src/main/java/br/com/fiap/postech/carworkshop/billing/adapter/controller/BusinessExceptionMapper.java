package br.com.fiap.postech.carworkshop.billing.adapter.controller;

import br.com.fiap.postech.carworkshop.billing.adapter.presenter.ErrorResponse;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotApprovableException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BudgetNotFoundException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.BusinessException;
import br.com.fiap.postech.carworkshop.billing.domain.exception.NotBudgetOwnerException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException exception) {
        int status = statusOf(exception);
        return Response.status(status)
                .entity(ErrorResponse.of(status, exception.getMessage()))
                .build();
    }

    static int statusOf(BusinessException exception) {
        if (exception instanceof BudgetNotFoundException) {
            return Response.Status.NOT_FOUND.getStatusCode();
        }
        if (exception instanceof NotBudgetOwnerException) {
            return Response.Status.FORBIDDEN.getStatusCode();
        }
        if (exception instanceof BudgetNotApprovableException) {
            return Response.Status.CONFLICT.getStatusCode();
        }
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}

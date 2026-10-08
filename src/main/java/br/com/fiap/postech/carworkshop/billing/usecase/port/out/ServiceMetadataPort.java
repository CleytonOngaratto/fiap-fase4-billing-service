package br.com.fiap.postech.carworkshop.billing.usecase.port.out;

/**
 * Where the service name and version come from (build/config), kept outside the use case.
 */
public interface ServiceMetadataPort {
    String name();

    String version();
}

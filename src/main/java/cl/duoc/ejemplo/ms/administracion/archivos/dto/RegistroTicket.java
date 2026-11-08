
package cl.duoc.ejemplo.ms.administracion.archivos.dto;

import java.time.Instant;

public record RegistroTicket(
    String id,
    String operacion,
    String evento,
    String usuario,
    String codigoTicket,
    int cantidad,
    int precioUnitario,
    long total,
    String rutaPdf,
    Instant fechaOperacion
) {}

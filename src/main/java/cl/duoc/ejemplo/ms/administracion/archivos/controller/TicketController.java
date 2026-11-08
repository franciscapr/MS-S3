
package cl.duoc.ejemplo.ms.administracion.archivos.controller;

import java.io.IOException;
import java.text.Normalizer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ejemplo.ms.administracion.archivos.service.EfsService;
import cl.duoc.ejemplo.ms.administracion.archivos.service.TicketPdfService;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketPdfService ticketPdfService;
    private final EfsService efsService;

    public TicketController(
            TicketPdfService ticketPdfService,
            EfsService efsService) {

        this.ticketPdfService = ticketPdfService;
        this.efsService = efsService;
    }

    @PostMapping(
        value = "/generar",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> generarTicket(
            @RequestBody TicketRequest request) throws IOException {

        // 1. Generar el PDF
        byte[] pdf = ticketPdfService.generarTicket(
            request.evento(),
            request.usuario(),
            request.codigoTicket(),
            request.cantidad(),
            request.precioUnitario()
        );

        // 2. Organizar la ruta por evento y usuario
        String nombreArchivo = nombreSeguro(request.codigoTicket()) + ".pdf";

        String rutaTicket =
                "evento-" + nombreSeguro(request.evento()) +
                "/usuario-" + nombreSeguro(request.usuario()) +
                "/" + nombreArchivo;

        // 3. Guardar el PDF en el almacenamiento configurado
        efsService.guardarTicketPdf(rutaTicket, pdf);

        // 4. Devolver el PDF a Postman
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + nombreArchivo + "\""
            )
            .contentLength(pdf.length)
            .body(pdf);
    }

    private String nombreSeguro(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Nombre invalido");
        }

        String limpio = Normalizer.normalize(
            valor.trim(), Normalizer.Form.NFD
        );

        limpio = limpio.replaceAll("\\p{M}", "");
        limpio = limpio.replaceAll("[^a-zA-Z0-9_-]", "_");

        if (limpio.isBlank() || limpio.replace("_", "").isBlank()) {
            throw new IllegalArgumentException("Nombre invalido");
        }

        return limpio.length() > 80
            ? limpio.substring(0, 80)
            : limpio;
    }

    public record TicketRequest(
        String evento,
        String usuario,
        String codigoTicket,
        int cantidad,
        int precioUnitario
    ) {}
}


package cl.duoc.ejemplo.ms.administracion.archivos.controller;

import java.io.IOException;
import java.text.Normalizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ejemplo.ms.administracion.archivos.service.AwsS3Service;
import cl.duoc.ejemplo.ms.administracion.archivos.service.EfsService;
import cl.duoc.ejemplo.ms.administracion.archivos.service.TicketPdfService;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketPdfService ticketPdfService;
    private final EfsService efsService;
    private final AwsS3Service awsS3Service;
    private final String bucket;

    public TicketController(
            TicketPdfService ticketPdfService,
            EfsService efsService,
            AwsS3Service awsS3Service,
            @Value("${tickets.s3.bucket}") String bucket) {

        this.ticketPdfService = ticketPdfService;
        this.efsService = efsService;
        this.awsS3Service = awsS3Service;
        this.bucket = bucket;
    }

    @PostMapping(
        value = "/generar",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> generarTicket(
            @RequestBody TicketRequest request) throws IOException {

        // 1. Generar el ticket PDF
        byte[] pdf = ticketPdfService.generarTicket(
            request.evento(),
            request.usuario(),
            request.codigoTicket(),
            request.cantidad(),
            request.precioUnitario()
        );

        // 2. Organizar el archivo por evento y usuario
        String nombreArchivo =
                nombreSeguro(request.codigoTicket()) + ".pdf";

        String rutaTicket =
                "evento-" + nombreSeguro(request.evento()) +
                "/usuario-" + nombreSeguro(request.usuario()) +
                "/" + nombreArchivo;

        // 3. Guardar el ticket PDF en Amazon EFS
        efsService.guardarTicketPdf(rutaTicket, pdf);

        // 4. Subir automaticamente el mismo PDF a Amazon S3
        awsS3Service.uploadPdf(bucket, rutaTicket, pdf);

        // 5. Devolver el PDF a Postman
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


package cl.duoc.ejemplo.ms.administracion.archivos.controller;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ejemplo.ms.administracion.archivos.dto.RegistroTicket;
import cl.duoc.ejemplo.ms.administracion.archivos.service.AwsS3Service;
import cl.duoc.ejemplo.ms.administracion.archivos.service.EfsService;
import cl.duoc.ejemplo.ms.administracion.archivos.service.HistorialTicketService;
import cl.duoc.ejemplo.ms.administracion.archivos.service.TicketPdfService;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketPdfService ticketPdfService;
    private final EfsService efsService;
    private final AwsS3Service awsS3Service;
    private final HistorialTicketService historialTicketService;
    private final String bucket;

    /**
     * CONSTRUCTOR
     * Inyeccion de dependencias de los servicios.
     */
    public TicketController(
            TicketPdfService ticketPdfService,
            EfsService efsService,
            AwsS3Service awsS3Service,
            HistorialTicketService historialTicketService,
            @Value("${tickets.s3.bucket}") String bucket) {

        this.ticketPdfService = ticketPdfService;
        this.efsService = efsService;
        this.awsS3Service = awsS3Service;
        this.historialTicketService = historialTicketService;
        this.bucket = bucket;
    }

    /**
     * GENERAR TICKET
     *
     * Genera un ticket PDF, lo guarda en EFS,
     * lo sube a S3 y registra su creacion
     * en el historial.
     *
     * POST /api/tickets/generar
     */
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

        // 2. Construir el nombre del archivo
        String nombreArchivo =
                nombreSeguro(request.codigoTicket()) + ".pdf";

        // 3. Organizar ruta por evento y usuario
        String rutaTicket =
                "evento-" + nombreSeguro(request.evento()) +
                "/usuario-" + nombreSeguro(request.usuario()) +
                "/" + nombreArchivo;

        // 4. Guardar el PDF en Amazon EFS
        efsService.guardarTicketPdf(rutaTicket, pdf);

        // 5. Subir automaticamente el PDF a Amazon S3
        awsS3Service.uploadPdf(
                bucket,
                rutaTicket,
                pdf
        );

        // 6. Registrar CREACION en el historial de S3
        historialTicketService.guardarRegistro(
                "CREACION",
                request.evento(),
                request.usuario(),
                request.codigoTicket(),
                request.cantidad(),
                request.precioUnitario(),
                rutaTicket
        );

        // 7. Devolver el PDF generado
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreArchivo + "\""
                )
                .contentLength(pdf.length)
                .body(pdf);
    }

    /**
     * ACTUALIZAR TICKET
     *
     * Verifica que el ticket exista,
     * genera una version actualizada del PDF,
     * lo reemplaza en EFS y S3 y registra
     * la actualizacion en el historial.
     *
     * PUT /api/tickets/actualizar
     */
    @PutMapping(
            value = "/actualizar",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> actualizarTicket(
            @RequestBody TicketRequest request) throws IOException {

        // 1. Identificar el ticket
        String nombreArchivo =
                nombreSeguro(request.codigoTicket()) + ".pdf";

        String rutaTicket =
                "evento-" + nombreSeguro(request.evento()) +
                "/usuario-" + nombreSeguro(request.usuario()) +
                "/" + nombreArchivo;

        // 2. Verificar que el ticket exista en S3
        awsS3Service.downloadAsBytes(
                bucket,
                rutaTicket
        );

        // 3. Generar el PDF actualizado
        byte[] pdfActualizado = ticketPdfService.generarTicket(
                request.evento(),
                request.usuario(),
                request.codigoTicket(),
                request.cantidad(),
                request.precioUnitario()
        );

        // 4. Reemplazar el PDF en Amazon EFS
        efsService.guardarTicketPdf(
                rutaTicket,
                pdfActualizado
        );

        // 5. Reemplazar el PDF en Amazon S3
        awsS3Service.uploadPdf(
                bucket,
                rutaTicket,
                pdfActualizado
        );

        // 6. Registrar ACTUALIZACION en el historial
        historialTicketService.guardarRegistro(
                "ACTUALIZACION",
                request.evento(),
                request.usuario(),
                request.codigoTicket(),
                request.cantidad(),
                request.precioUnitario(),
                rutaTicket
        );

        // 7. Devolver el PDF actualizado
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreArchivo + "\""
                )
                .contentLength(pdfActualizado.length)
                .body(pdfActualizado);
    }

    /**
     * ELIMINAR TICKET
     *
     * Verifica la existencia del ticket,
     * elimina el PDF de EFS y S3
     * y registra la eliminacion.
     *
     * DELETE /api/tickets
     */
    @DeleteMapping
    public ResponseEntity<Void> eliminarTicket(
            @RequestParam String evento,
            @RequestParam String usuario,
            @RequestParam String codigoTicket) throws IOException {

        // 1. Construir la ruta del ticket
        String nombreArchivo =
                nombreSeguro(codigoTicket) + ".pdf";

        String rutaTicket =
                "evento-" + nombreSeguro(evento) +
                "/usuario-" + nombreSeguro(usuario) +
                "/" + nombreArchivo;

        // 2. Verificar que el ticket existe en S3
        awsS3Service.downloadAsBytes(
                bucket,
                rutaTicket
        );

        // 3. Eliminar el PDF de Amazon EFS
        efsService.eliminarTicketPdf(rutaTicket);

        // 4. Eliminar el PDF de Amazon S3
        awsS3Service.deleteObject(
                bucket,
                rutaTicket
        );

        // 5. Registrar ELIMINACION en el historial
        historialTicketService.guardarRegistro(
                "ELIMINACION",
                evento,
                usuario,
                codigoTicket,
                0,
                0,
                rutaTicket
        );

        // 6. Devolver HTTP 204
        return ResponseEntity.noContent().build();
    }

    /**
     * CONSULTAR HISTORIAL DE TICKETS
     *
     * Recupera las operaciones registradas
     * en Amazon S3.
     *
     * Permite filtrar por evento y usuario.
     *
     * GET /api/tickets/historial
     */
    @GetMapping("/historial")
    public ResponseEntity<List<RegistroTicket>> consultarHistorial(
            @RequestParam(required = false) String evento,
            @RequestParam(required = false) String usuario)
            throws IOException {

        // 1. Consultar el historial almacenado en S3
        List<RegistroTicket> historial =
                historialTicketService.consultarHistorial(
                        evento,
                        usuario
                );

        // 2. Devolver los registros en formato JSON
        return ResponseEntity.ok(historial);
    }

    /**
     * CONSULTAR ESTADISTICAS DE RESERVAS
     *
     * Calcula las estadisticas a partir
     * del historial almacenado en Amazon S3.
     *
     * Permite filtrar por evento y usuario.
     *
     * GET /api/tickets/estadisticas
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<HistorialTicketService.EstadisticasTicket>
            consultarEstadisticas(
                    @RequestParam(required = false) String evento,
                    @RequestParam(required = false) String usuario)
                    throws IOException {

        // 1. Calcular estadisticas con filtros opcionales
        HistorialTicketService.EstadisticasTicket estadisticas =
                historialTicketService.calcularEstadisticas(
                        evento,
                        usuario
                );

        // 2. Devolver las estadisticas en formato JSON
        return ResponseEntity.ok(estadisticas);
    }

    /**
     * GENERAR NOMBRE SEGURO
     *
     * Normaliza los nombres utilizados
     * para construir las rutas en EFS y S3.
     */
    private String nombreSeguro(String valor) {

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Nombre invalido"
            );
        }

        // 1. Normalizar el texto
        String limpio = Normalizer.normalize(
                valor.trim(),
                Normalizer.Form.NFD
        );

        // 2. Eliminar tildes
        limpio = limpio.replaceAll("\\p{M}", "");

        // 3. Reemplazar caracteres especiales
        limpio = limpio.replaceAll(
                "[^a-zA-Z0-9_-]",
                "_"
        );

        // 4. Validar el nombre resultante
        if (limpio.isBlank()
                || limpio.replace("_", "").isBlank()) {

            throw new IllegalArgumentException(
                    "Nombre invalido"
            );
        }

        // 5. Limitar la longitud
        return limpio.length() > 80
                ? limpio.substring(0, 80)
                : limpio;
    }

    /**
     * DATOS RECIBIDOS DESDE POSTMAN
     */
    public record TicketRequest(
            String evento,
            String usuario,
            String codigoTicket,
            int cantidad,
            int precioUnitario
    ) {}
}

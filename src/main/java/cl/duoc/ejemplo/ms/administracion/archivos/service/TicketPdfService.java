
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class TicketPdfService {

    public byte[] generarTicket(
            String evento,
            String usuario,
            String codigoTicket,
            int cantidad,
            int precioUnitario) {

        if (evento == null || usuario == null || codigoTicket == null
                || cantidad <= 0 || precioUnitario < 0) {
            throw new IllegalArgumentException("Datos del ticket invalidos");
        }

        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);

            try (PDPageContentStream contenido =
                    new PDPageContentStream(documento, pagina)) {

                escribir(contenido, "TICKET DE RESERVA", 50, 750, 20, true);

                escribir(contenido, "Codigo: " + codigoTicket,
                        50, 700, 12, false);

                escribir(contenido, "Evento: " + evento,
                        50, 670, 12, false);

                escribir(contenido, "Usuario: " + usuario,
                        50, 640, 12, false);

                escribir(contenido, "Cantidad: " + cantidad,
                        50, 610, 12, false);

                escribir(contenido, "Precio unitario: $" + precioUnitario,
                        50, 580, 12, false);

                escribir(contenido,
                        "Total: $" + ((long) cantidad * precioUnitario),
                        50, 550, 12, true);

                String fecha = LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                escribir(contenido, "Fecha de emision: " + fecha,
                        50, 520, 12, false);

                escribir(contenido, "Gracias por tu reserva",
                        50, 470, 12, false);
            }

            documento.save(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el ticket PDF", e);
        }
    }

    private void escribir(
            PDPageContentStream contenido,
            String texto,
            float x,
            float y,
            int tamano,
            boolean negrita) throws IOException {

        Standard14Fonts.FontName fuente = negrita
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA;

        contenido.beginText();
        contenido.setFont(new PDType1Font(fuente), tamano);
        contenido.newLineAtOffset(x, y);
        contenido.showText(texto);
        contenido.endText();
    }
}

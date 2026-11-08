
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EfsService {

    @Value("${efs.path}")
    private String efsPath;

    // Metodo original: guardar archivos enviados desde Postman
    public File saveToEfs(
            String filename,
            MultipartFile multipartFile) throws IOException {

        Path destino = obtenerRutaSegura(filename);

        Files.createDirectories(destino.getParent());
        multipartFile.transferTo(destino);

        return destino.toFile();
    }

    // Nuevo metodo: guardar tickets PDF generados por Spring Boot
    public Path guardarTicketPdf(
            String rutaTicket,
            byte[] contenidoPdf) throws IOException {

        if (contenidoPdf == null || contenidoPdf.length == 0) {
            throw new IllegalArgumentException(
                    "El contenido del PDF no puede estar vacio");
        }

        Path destino = obtenerRutaSegura(rutaTicket);

        Files.createDirectories(destino.getParent());
        Files.write(destino, contenidoPdf);

        return destino;
    }

    // Validar que los archivos se guarden dentro de la ruta EFS
    private Path obtenerRutaSegura(String ruta) {

        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("Ruta invalida");
        }

        Path base = Paths.get(efsPath).toAbsolutePath().normalize();
        Path destino = base.resolve(ruta).normalize();

        if (!destino.startsWith(base) || destino.equals(base)) {
            throw new IllegalArgumentException(
                    "No se permite guardar archivos fuera de EFS");
        }

        return destino;
    }

	
	/**
	 * Elimina un ticket PDF almacenado en Amazon EFS.
	 *
	 * @param rutaTicket Ruta relativa del ticket dentro de EFS
	 * @return true si el archivo existia y fue eliminado;
	 *         false si no existia.
	 */
	public boolean eliminarTicketPdf(String rutaTicket)
			throws IOException {

		// Obtener la ruta validada dentro de EFS
		Path destino = obtenerRutaSegura(rutaTicket);

		// Verificar que solamente se eliminen archivos PDF
		if (!destino.getFileName().toString().endsWith(".pdf")) {
			throw new IllegalArgumentException(
					"Solo se permite eliminar archivos PDF");
		}

		// Eliminar el archivo si existe
		return Files.deleteIfExists(destino);
	}

}

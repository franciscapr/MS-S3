package cl.duoc.ejemplo.ms.administracion.archivos.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cl.duoc.ejemplo.ms.administracion.archivos.dto.S3ObjectDto;
import cl.duoc.ejemplo.ms.administracion.archivos.service.AwsS3Service;
import cl.duoc.ejemplo.ms.administracion.archivos.service.EfsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
public class AwsS3Controller {

	@Autowired
	private AwsS3Service awsS3Service;

	@Autowired
	private EfsService efsService;

	/**
	 * Lista todos los objetos en un bucket de S3
	 * 
	 * @param bucket Nombre del bucket
	 * @return Lista de objetos con sus metadatos
	 */
	@GetMapping("/{bucket}/objects")
	public ResponseEntity<List<S3ObjectDto>> listObjects(@PathVariable String bucket) {

		List<S3ObjectDto> dtoList = awsS3Service.listObjects(bucket);
		return ResponseEntity.ok(dtoList);
	}

	/**
	 * Descarga un objeto de S3 como array de bytes
	 * 
	 * @param bucket Nombre del bucket
	 * @param key    Clave del objeto a descargar
	 * @return Archivo descargado como bytes
	 */
	@GetMapping("/{bucket}/object")
	public ResponseEntity<byte[]> downloadObject(@PathVariable String bucket, @RequestParam String key) {

		byte[] fileBytes = awsS3Service.downloadAsBytes(bucket, key);

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
				.contentType(MediaType.APPLICATION_OCTET_STREAM).body(fileBytes);
	}

	/**
	 * Sube un archivo a S3 y lo almacena en EFS
	 * 
	 * @param bucket Nombre del bucket
	 * @param key    Clave del objeto
	 * @param file   Archivo a subir
	 * @return Respuesta de éxito
	 */
	@PostMapping("/{bucket}/object")
	public ResponseEntity<Void> uploadObject(@PathVariable String bucket, @RequestParam String key,
			@RequestParam("file") MultipartFile file) {

		try {

			efsService.saveToEfs(key, file);

			awsS3Service.upload(bucket, key, file);

			return ResponseEntity.status(HttpStatus.CREATED).build();
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Mueve un objeto dentro del mismo bucket
	 * 
	 * @param bucket    Nombre del bucket
	 * @param sourceKey Clave del objeto origen
	 * @param destKey   Clave del objeto destino
	 * @return Respuesta de éxito
	 */
	@PostMapping("/{bucket}/move")
	public ResponseEntity<Void> moveObject(@PathVariable String bucket, @RequestParam String sourceKey,
			@RequestParam String destKey) {

		awsS3Service.moveObject(bucket, sourceKey, destKey);
		return ResponseEntity.ok().build();
	}

	/**
	 * Elimina un objeto de S3
	 * 
	 * @param bucket Nombre del bucket
	 * @param key    Clave del objeto a eliminar
	 * @return Respuesta sin contenido
	 */
	@DeleteMapping("/{bucket}/object")
	public ResponseEntity<Void> deleteObject(@PathVariable String bucket, @RequestParam String key) {

		awsS3Service.deleteObject(bucket, key);
		return ResponseEntity.noContent().build();
	}
}

package cl.duoc.ejemplo.ms.administracion.archivos.exception;

public class S3ObjectNotFoundException extends RuntimeException {

	public S3ObjectNotFoundException(String key, String bucketName) {
		super("El objeto '" + key + "' no existe en el bucket '" + bucketName + "'");
	}

	public S3ObjectNotFoundException(String key, String bucketName, Throwable cause) {
		super("El objeto '" + key + "' no existe en el bucket '" + bucketName + "'", cause);
	}
}

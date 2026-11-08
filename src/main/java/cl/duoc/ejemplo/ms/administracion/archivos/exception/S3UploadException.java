package cl.duoc.ejemplo.ms.administracion.archivos.exception;

public class S3UploadException extends RuntimeException {

	public S3UploadException(String message) {
		super(message);
	}

	public S3UploadException(String message, Throwable cause) {
		super(message, cause);
	}
}

package cl.duoc.ejemplo.ms.administracion.archivos.exception;

public class S3OperationException extends RuntimeException {

	public S3OperationException(String message) {
		super(message);
	}

	public S3OperationException(String message, Throwable cause) {
		super(message, cause);
	}
}

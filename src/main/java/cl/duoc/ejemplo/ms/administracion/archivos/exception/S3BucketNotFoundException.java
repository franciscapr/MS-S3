package cl.duoc.ejemplo.ms.administracion.archivos.exception;

public class S3BucketNotFoundException extends RuntimeException {
    
    public S3BucketNotFoundException(String bucketName) {
        super("El bucket '" + bucketName + "' no existe en S3");
    }
    
    public S3BucketNotFoundException(String bucketName, Throwable cause) {
        super("El bucket '" + bucketName + "' no existe en S3", cause);
    }
}

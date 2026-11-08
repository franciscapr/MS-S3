
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.ejemplo.ms.administracion.archivos.dto.RegistroTicket;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class HistorialTicketService {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucket;

    // Constructor con inyeccion de dependencias
    public HistorialTicketService(
            S3Client s3Client,
            ObjectMapper objectMapper,
            @Value("${tickets.s3.bucket}") String bucket) {

        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
    }

    /**
     * GUARDAR REGISTRO
     *
     * Registra una operacion sobre un ticket
     * y guarda un archivo JSON en Amazon S3.
     *
     * Operaciones permitidas:
     * CREACION, ACTUALIZACION y ELIMINACION.
     */
    public RegistroTicket guardarRegistro(
            String operacion,
            String evento,
            String usuario,
            String codigoTicket,
            int cantidad,
            int precioUnitario,
            String rutaPdf) throws JsonProcessingException {

        // 1. Validar tipo de operacion
        if (!Set.of(
                "CREACION",
                "ACTUALIZACION",
                "ELIMINACION"
        ).contains(operacion)) {

            throw new IllegalArgumentException(
                    "Operacion de ticket no valida"
            );
        }

        // 2. Validar cantidad y precio
        if (cantidad < 0 || precioUnitario < 0) {
            throw new IllegalArgumentException(
                    "Cantidad o precio invalido"
            );
        }

        // 3. Generar identificador unico
        String id = UUID.randomUUID().toString();

        // 4. Calcular total de la reserva
        long total = (long) cantidad * precioUnitario;

        // 5. Crear el registro del historial
        RegistroTicket registro = new RegistroTicket(
                id,
                operacion,
                evento,
                usuario,
                codigoTicket,
                cantidad,
                precioUnitario,
                total,
                rutaPdf,
                Instant.now()
        );

        // 6. Convertir el registro Java a JSON
        byte[] contenidoJson =
                objectMapper.writeValueAsBytes(registro);

        // 7. Definir ubicacion del JSON en S3
        String claveS3 = "historial/" + id + ".json";

        // 8. Preparar solicitud para Amazon S3
        PutObjectRequest solicitud = PutObjectRequest.builder()
                .bucket(bucket)
                .key(claveS3)
                .contentType("application/json")
                .build();

        // 9. Guardar historial en Amazon S3
        s3Client.putObject(
                solicitud,
                RequestBody.fromBytes(contenidoJson)
        );

        // 10. Devolver registro creado
        return registro;
    }

    /**
     * CONSULTAR HISTORIAL
     *
     * Obtiene los registros JSON almacenados
     * en Amazon S3.
     *
     * Permite filtrar por evento y usuario.
     *
     * Si no se especifican filtros,
     * devuelve todos los registros.
     */
    public List<RegistroTicket> consultarHistorial(
            String evento,
            String usuario) throws IOException {

        List<RegistroTicket> historial = new ArrayList<>();

        // 1. Buscar registros en Amazon S3
        ListObjectsV2Request solicitud = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("historial/")
                .build();

        // 2. Recorrer todos los registros disponibles
        // El paginador permite consultar mas de 1000 objetos
        for (S3Object objeto :
                s3Client.listObjectsV2Paginator(solicitud).contents()) {

            // Procesar solamente archivos JSON
            if (!objeto.key().endsWith(".json")) {
                continue;
            }

            // 3. Preparar descarga del registro
            GetObjectRequest descarga = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objeto.key())
                    .build();

            // 4. Descargar contenido JSON desde S3
            byte[] contenido = s3Client
                    .getObjectAsBytes(descarga)
                    .asByteArray();

            // 5. Convertir JSON a RegistroTicket
            RegistroTicket registro = objectMapper.readValue(
                    contenido,
                    RegistroTicket.class
            );

            // 6. Aplicar filtro por evento
            boolean coincideEvento =
                    evento == null
                    || evento.isBlank()
                    || registro.evento()
                            .equalsIgnoreCase(evento.trim());

            // 7. Aplicar filtro por usuario
            boolean coincideUsuario =
                    usuario == null
                    || usuario.isBlank()
                    || registro.usuario()
                            .equalsIgnoreCase(usuario.trim());

            // 8. Agregar registros coincidentes
            if (coincideEvento && coincideUsuario) {
                historial.add(registro);
            }
        }

        // 9. Ordenar del mas reciente al mas antiguo
        historial.sort(
                Comparator.comparing(
                        RegistroTicket::fechaOperacion
                ).reversed()
        );

        // 10. Devolver historial filtrado
        return historial;
    }

    /**
     * CALCULAR ESTADISTICAS
     *
     * Calcula las estadisticas utilizando el historial
     * de operaciones almacenado en Amazon S3.
     *
     * Considera solamente el ultimo estado
     * registrado de cada ticket.
     *
     * Permite filtrar por evento y usuario.
     */
    public EstadisticasTicket calcularEstadisticas(
            String evento,
            String usuario) throws IOException {

        // 1. Obtener historial filtrado
        List<RegistroTicket> registros =
                consultarHistorial(evento, usuario);

        // 2. Guardar la ultima operacion de cada ticket
        // El historial ya viene ordenado del mas reciente
        // al mas antiguo.
        Map<String, RegistroTicket> ultimoEstado =
                new HashMap<>();

        for (RegistroTicket registro : registros) {

            // La ruta del PDF identifica cada ticket
            ultimoEstado.putIfAbsent(
                    registro.rutaPdf(),
                    registro
            );
        }

        // 3. Inicializar contadores
        long reservasActivas = 0;
        long reservasEliminadas = 0;
        long entradasReservadas = 0;
        long montoTotalReservado = 0;

        // 4. Analizar ultimo estado de cada ticket
        for (RegistroTicket registro : ultimoEstado.values()) {

            if ("ELIMINACION".equals(registro.operacion())) {

                // El ticket ya no esta activo
                reservasEliminadas++;

            } else if (
                    "CREACION".equals(registro.operacion())
                    || "ACTUALIZACION".equals(registro.operacion())
            ) {

                // El ticket continua activo
                reservasActivas++;

                // Sumar cantidad de entradas
                entradasReservadas += registro.cantidad();

                // Sumar valor de las reservas activas
                montoTotalReservado += registro.total();
            }
        }

        // 5. Devolver estadisticas
        return new EstadisticasTicket(
                registros.size(),
                reservasActivas,
                reservasEliminadas,
                entradasReservadas,
                montoTotalReservado
        );
    }

    /**
     * ESTRUCTURA DE RESPUESTA DE ESTADISTICAS
     *
     * Se serializa automaticamente como JSON
     * cuando se devuelve desde el controlador REST.
     */
    public record EstadisticasTicket(
            long totalOperaciones,
            long reservasActivas,
            long reservasEliminadas,
            long entradasReservadas,
            long montoTotalReservado
    ) {}
}

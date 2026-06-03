# Paper microservice

Microservicio Spring Boot para administrar papers de conferencias, sus adjuntos y
archivos de soporte de conferencia. Expone una API HTTP protegida con JWT, persiste
metadatos en PostgreSQL y guarda los bytes de archivos en Backblaze B2 mediante la
API compatible con S3.

## Arquitectura

- **API web:** `PaperController` publica `/papers/**` y `ConferenceFileController`
  publica `/files/**`.
- **Dominio:** `Paper` representa un envio asociado a `conferenceId`; `PaperAttachment`
  representa sus adjuntos; `ConferenceSupportFile` representa archivos compartidos
  de la conferencia.
- **Persistencia:** Spring Data JPA con PostgreSQL. Hibernate usa `ddl-auto=update`.
- **Almacenamiento:** `FileStorageService` sube, descarga y elimina objetos en el
  bucket configurado de Backblaze B2/S3. Las entidades guardan la clave del objeto y
  no guardan el contenido binario en la base de datos.
- **Optimizacion de archivos:** `FileOptimizer` intenta reducir PDFs, JPEGs y PNGs
  antes de subirlos. Otros tipos se guardan sin cambios.
- **Mensajeria:** al evaluar un paper, `PaperService.evaluate` publica un evento
  JSON `paper.evaluated` en RabbitMQ usando `RabbitTemplate` y el exchange/routing
  key configurados por entorno.
- **Seguridad:** OAuth2 resource server con JWT HMAC SHA-256. Swagger queda publico;
  el resto requiere autenticacion salvo reglas de rol especificas.

## Modelo de datos y contratos

| Entidad | Proposito | Campos operativos |
| --- | --- | --- |
| `Paper` | Envio de un articulo para una conferencia. | `conferenceId`, datos bibliograficos, `status`, `evaluationObservations`. |
| `PaperAttachment` | Archivo adjunto a un paper. | `paper_id`, `objectName`, `originalFileName`, `contentType`, `fileSize`. |
| `ConferenceSupportFile` | Archivo compartido a nivel de conferencia. | `conferenceId`, `minioObjectName`, `originalFileName`, `contentType`, `fileSize`. |

Contratos importantes:

- Los IDs de conferencia llegan por path y no se validan contra otro servicio desde
  este microservicio.
- La base de datos guarda metadatos y claves de objeto; los bytes viven solo en
  Backblaze B2/S3.
- `fileSize` siempre representa el tamano subido despues de optimizar, no el tamano
  original recibido.
- Las claves de objeto se generan como `UUID + extension`. Si el optimizador cambia
  el MIME, la extension se ajusta para coincidir con el contenido almacenado.
- Los adjuntos de papers usan cascade/orphan removal desde `Paper`; los archivos de
  conferencia se borran explicitamente desde `ConferenceFileService.deleteFile`.
- La evaluacion de papers emite un evento de dominio para consumidores externos; no
  hay outbox ni reintentos declarados en este servicio.

## Configuracion local

Requisitos:

- Java 21.
- Maven Wrapper incluido (`./mvnw`).
- PostgreSQL accesible.
- RabbitMQ accesible.
- Credenciales de Backblaze B2 o un servicio S3-compatible.

Crear un archivo `.env` en la raiz a partir de `.env.example`:

```properties
PORT=8083
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paper
SPRING_DATASOURCE_USERNAME=paper
SPRING_DATASOURCE_PASSWORD=paper
FRONTEND_URL=http://localhost:3000
JWT_PUBLIC_KEY=replace-with-hmac-secret
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/
RABBITMQ_SSL_ENABLED=false
RABBITMQ_EXCHANGE=paper.events
RABBITMQ_ROUTING_KEY_EVALUATED=paper.evaluated
BACKBLAZE_ENDPOINT=https://s3.us-west-004.backblazeb2.com
BACKBLAZE_REGION=us-west-004
BACKBLAZE_BUCKET_NAME=paper-files
BACKBLAZE_ACCESS_KEY=replace-with-key-id
BACKBLAZE_SECRET_KEY=replace-with-application-key
```

Notas:

- `JWT_PUBLIC_KEY` es el nombre de la variable, pero el codigo la usa como secreto
  HMAC SHA-256 (`NimbusJwtDecoder.withSecretKey`).
- `FRONTEND_URL` se usa como patron permitido de CORS.
- `spring.config.import` carga `.env` de forma opcional; las variables obligatorias
  deben existir en el entorno cuando no haya `.env`.
- RabbitMQ se configura con las propiedades `spring.rabbitmq.*`; en desarrollo local
  normalmente se usa `RABBITMQ_SSL_ENABLED=false`, mientras que el valor por defecto
  de la aplicacion es `true` si la variable no existe.
- `RABBITMQ_EXCHANGE` define el bean `TopicExchange`. `RABBITMQ_ROUTING_KEY_EVALUATED`
  se usa al publicar evaluaciones completadas.

Comandos utiles:

```bash
./mvnw test
./mvnw spring-boot:run
docker build -t paper-service .
```

La aplicacion escucha en `PORT` o `8083` por defecto. Swagger esta disponible en
`/swagger-ui.html` y el documento OpenAPI en `/v3/api-docs`.

## Autenticacion y roles

Los JWT pueden declarar roles en cualquiera de estos claims:

- `roles`: lista de strings o string separado por comas.
- `role`: string, tambien puede contener valores separados por comas.
- `authority`: string, tambien puede contener valores separados por comas.

Cada valor se normaliza agregando el prefijo `ROLE_` cuando no viene presente.

Reglas relevantes:

| Ruta | Rol requerido |
| --- | --- |
| `POST /papers/conference/{conferenceId}/create` | Publico segun `SecurityConfig`; validar acceso en capas externas si aplica. |
| `PATCH /papers/conference/{conferenceId}/{paperId}/evaluations` | Publico segun `SecurityConfig`; validar acceso en capas externas si aplica. |
| `POST /files/upload/{conferenceId}` | `ADMIN` o `CHAIR` |
| `DELETE /files/delete/{fileId}` | `ADMIN` o `CHAIR` |
| Swagger y OpenAPI | publico |
| Resto de rutas | JWT autenticado |

## API de papers

### Crear paper

`POST /papers/conference/{conferenceId}/create`

Content-Type: `multipart/form-data`

- Parte `paper`: JSON compatible con `PaperCreateDto`.
- Parte opcional `files`: uno o varios archivos.

Ejemplo:

```bash
curl -X POST "http://localhost:8083/papers/conference/$CONFERENCE_ID/create" \
  -F 'paper={"title":"Titulo","abstractText":"Resumen","topic":"IA","institutionalAffiliation":"Universidad","keywords":"ia,ml","authors":"Ada Lovelace"};type=application/json' \
  -F "files=@paper.pdf"
```

Campos obligatorios de `paper`: `title`, `abstractText`, `topic`,
`institutionalAffiliation`, `keywords` y `authors`. El servicio recorta espacios y
crea el paper con estado `SUBMITTED`.

### Consultar papers

| Metodo y ruta | Uso |
| --- | --- |
| `GET /papers/conference/{conferenceId}/list` | Lista papers de la conferencia. Acepta `?status=SUBMITTED` u otro valor de `PaperStatus`. |
| `GET /papers/conference/{conferenceId}/evaluations-tray` | Lista papers `SUBMITTED` para evaluacion. |
| `GET /papers/conference/{conferenceId}/{paperId}` | Obtiene un paper con sus documentos. |

Estados validos: `SUBMITTED`, `ACCEPTED`, `REJECTED`, `IN_CORRECTIONS`,
`PRESENTED`, `PUBLISHED`.

Las respuestas incluyen `documents` con `id`, `originalFileName`, `contentType` y
`fileSize`, ademas de `hasDocument` para clientes que solo necesitan un booleano.

### Evaluar paper

`PATCH /papers/conference/{conferenceId}/{paperId}/evaluations`

```json
{
  "status": "ACCEPTED",
  "observations": "Cumple con los criterios."
}
```

`status` es obligatorio y debe ser un valor de `PaperStatus`; `observations` es
opcional y se guarda recortado cuando viene informado.

Efecto secundario: despues de guardar el nuevo estado, el servicio publica el evento
`paper.evaluated` en RabbitMQ. Si la publicacion falla, el error no se captura en
`PaperService.evaluate`, por lo que la peticion puede fallar y la transaccion JPA
puede revertirse.

### Evento `paper.evaluated`

Codigo principal: `PaperService.evaluate`, `RabbitMQConfig` y `PaperEvaluatedEvent`.

El mensaje se serializa como JSON con `Jackson2JsonMessageConverter` y se envia al
`TopicExchange` configurado en `RABBITMQ_EXCHANGE` usando
`RABBITMQ_ROUTING_KEY_EVALUATED`.

Ejemplo de payload:

```json
{
  "eventType": "paper.evaluated",
  "eventVersion": "1.0",
  "eventId": "7b0a2a54-8a1a-47d7-8fb5-2d63b4d2c31f",
  "occurredAt": "2026-06-03T11:00:00Z",
  "source": "paper-service",
  "data": {
    "paperId": "0d8d5f64-52f7-4f73-a126-1a5c12a14a21",
    "conferenceId": "a9eb3a7a-d6fc-4e04-bd13-3a21f8f4a5c9",
    "title": "Titulo",
    "topic": "IA",
    "status": "ACCEPTED",
    "evaluationObservations": "Cumple con los criterios.",
    "evaluatedBy": {
      "userId": "a4904a58-5b7c-4d08-9270-f4bc6b744e5e",
      "role": "CHAIR"
    },
    "authors": [
      {
        "name": "Ada Lovelace",
        "email": "author@example.com"
      }
    ]
  }
}
```

Restricciones actuales del contrato:

- `eventType`, `eventVersion` y `source` son constantes: `paper.evaluated`, `1.0`
  y `paper-service`.
- `eventId` y `occurredAt` se generan durante la evaluacion.
- `data.status` se toma de `PaperStatus.name()`.
- `authors` se deriva separando el campo string `Paper.authors` por comas. El email
  se publica como placeholder `author@example.com` porque el modelo actual no guarda
  emails de autores por separado.
- `evaluatedBy.userId` se genera aleatoriamente y `evaluatedBy.role` se publica como
  `CHAIR`; no se deriva del JWT ni de un usuario autenticado.
- El servicio no declara colas ni bindings para consumidores; solo declara el exchange
  y publica el mensaje.

### Adjuntos de paper

| Metodo y ruta | Uso |
| --- | --- |
| `POST /papers/conference/{conferenceId}/{paperId}/attachments` | Sube nuevos archivos al paper usando `multipart/form-data` con parametro `files`. |
| `GET /papers/conference/{conferenceId}/{paperId}/attachments/{attachmentId}` | Descarga un adjunto validando que pertenezca al paper y conferencia. |

La descarga devuelve `Content-Type` guardado y `Content-Disposition: attachment`
con el nombre original cuando esta disponible.

## API de archivos de conferencia

| Metodo y ruta | Uso |
| --- | --- |
| `POST /files/upload/{conferenceId}` | Sube un archivo de soporte con parametro `file`. |
| `GET /files/list/{conferenceId}` | Lista archivos de soporte de una conferencia. |
| `GET /files/{conferenceId}/download/{fileId}` | Descarga un archivo validando su conferencia. |
| `DELETE /files/delete/{fileId}` | Elimina el objeto en Backblaze y luego el registro JPA. |

Los archivos de conferencia no estan ligados a un paper; solo a `conferenceId`.

## Flujo de almacenamiento y optimizacion

1. El controlador recibe `MultipartFile`.
2. El servicio llama a `FileOptimizer`.
3. PDFs se reescriben con PDFBox y solo reemplazan al original si reducen bytes.
4. JPEGs se recomprimen con calidad `0.82` si el resultado es menor.
5. PNGs sin canal alpha pueden convertirse a JPEG si eso reduce el tamano; PNGs con
   alpha se conservan.
6. `FileStorageService` genera una clave `UUID + extension`, sube los bytes al bucket
   y guarda metadatos en PostgreSQL.

Si el tipo MIME falta, el optimizador lo infiere por extension para `.pdf`, `.jpg`,
`.jpeg` y `.png`; cualquier otro archivo usa `application/octet-stream`.

Restricciones del flujo:

- Si la optimizacion falla para un PDF ilegible o protegido, se suben los bytes
  originales con `application/pdf`.
- Si una imagen no puede leerse con `ImageIO`, se sube sin cambios con el MIME
  inferido.
- PNGs con transparencia no se convierten a JPEG para no perder canal alpha.
- No hay limite de tamano declarado en la aplicacion; revisar limites del gateway,
  contenedor o servidor antes de aceptar archivos grandes.

## Runbook operativo

### Alta de papers con adjuntos

Codigo principal: `PaperController.create` -> `PaperService.create` ->
`FileOptimizer` -> `FileStorageService.uploadOptimized`.

1. Enviar `multipart/form-data` con parte `paper` como JSON y parte opcional
   `files`.
2. Confirmar que la respuesta incluye `documents` cuando se subieron archivos.
3. Si la respuesta es 500 durante la carga, revisar conectividad y permisos del
   bucket; la subida al bucket ocurre antes de guardar el registro JPA.

`SecurityConfig` no exige JWT para este endpoint; si el despliegue requiere autores
autenticados, esa validacion debe existir en una capa externa o cambiarse en este
servicio.

### Evaluacion de papers

Codigo principal: `PaperController.evaluate` -> `PaperService.evaluate`.

- Solo cambia `status` y `evaluationObservations`.
- `status` debe ser uno de `PaperStatus`.
- El paper se busca por `paperId` y `conferenceId`; si no coinciden devuelve 404.
- Despues del guardado se envia `paper.evaluated` a RabbitMQ con el contrato anterior.
- No hay outbox ni mecanismo de retry local; revisar logs y estado del broker si la
  evaluacion devuelve error durante la publicacion.

### Archivos de soporte de conferencia

Codigo principal: `ConferenceFileController` -> `ConferenceFileService`.

- `POST /files/upload/{conferenceId}` guarda un unico parametro `file`.
- `GET /files/list/{conferenceId}` lista solo metadatos, no descarga bytes.
- `GET /files/{conferenceId}/download/{fileId}` valida que el archivo pertenezca a
  la conferencia antes de descargar desde Backblaze.
- `DELETE /files/delete/{fileId}` elimina primero el objeto del bucket y luego el
  registro en PostgreSQL.

### Recuperacion ante inconsistencias

| Sintoma | Revision | Accion segura |
| --- | --- | --- |
| Registro existe, descarga falla | La clave `objectName`/`minioObjectName` no existe en el bucket o no hay permisos. | Restaurar el objeto con la misma clave o eliminar el registro si ya no debe exponerse. |
| Objeto existe, registro no aparece | La transaccion JPA fallo despues de subir bytes. | Validar si la clave aparece en logs o en el bucket; si no hay registro asociado, borrar el objeto huerfano. |
| Delete devuelve 500 | `deleteObject` fallo antes de borrar la fila. | Corregir credenciales/permisos de Backblaze y reintentar el delete. |
| Tipo o extension no coinciden con el archivo original | El optimizador pudo convertir PNG sin alpha a JPEG o normalizar extension por MIME. | Usar `contentType` de la respuesta como fuente de verdad para clientes. |
| Evaluacion devuelve 500 o no llega evento | RabbitMQ no esta accesible, credenciales/SSL/vhost son incorrectos o faltan `RABBITMQ_EXCHANGE`/`RABBITMQ_ROUTING_KEY_EVALUATED`. | Validar variables `RABBITMQ_*`, conectividad al broker y que exista binding de consumidor para el routing key. |

## Troubleshooting

- **La aplicacion no arranca por propiedades faltantes:** revisar
  `SPRING_DATASOURCE_URL`, `FRONTEND_URL`, `JWT_PUBLIC_KEY` y variables `BACKBLAZE_*`.
- **JWT valido pero sin permisos:** verificar que el claim use `roles`, `role` o
  `authority` y que contenga `ADMIN` o `CHAIR` para las rutas protegidas de
  carga/eliminacion de archivos de conferencia.
- **CORS bloquea el frontend:** confirmar que `FRONTEND_URL` coincide con el origen
  real del navegador. Se admiten patrones porque se usa `setAllowedOriginPatterns`.
- **Descargas devuelven 404:** confirmar que el ID pertenezca a la misma conferencia
  y paper cuando aplica; los servicios filtran por esos IDs antes de descargar.
- **Archivos no se eliminan del bucket:** la eliminacion requiere que el cliente S3
  use path-style access, habilitado en `BackblazeConfig`.
- **Extensiones inesperadas:** si el optimizador cambia el tipo MIME, la clave del
  objeto usa extension compatible con el contenido optimizado.
- **La aplicacion no arranca por RabbitMQ:** revisar `RABBITMQ_HOST`,
  `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`,
  `RABBITMQ_SSL_ENABLED`, `RABBITMQ_EXCHANGE` y `RABBITMQ_ROUTING_KEY_EVALUATED`.
- **Consumidores no reciben evaluaciones:** este servicio solo publica en el exchange;
  las colas y bindings deben existir en el consumidor o la infraestructura RabbitMQ.

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

## Configuracion local

Requisitos:

- Java 21.
- Maven Wrapper incluido (`./mvnw`).
- PostgreSQL accesible.
- Credenciales de Backblaze B2 o un servicio S3-compatible.

Crear un archivo `.env` en la raiz a partir de `.env.example`:

```properties
PORT=8083
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paper
SPRING_DATASOURCE_USERNAME=paper
SPRING_DATASOURCE_PASSWORD=paper
FRONTEND_URL=http://localhost:3000
JWT_PUBLIC_KEY=replace-with-hmac-secret
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
| `POST /papers/conference/{conferenceId}/create` | `ADMIN` o `AUTHOR` |
| `PATCH /papers/conference/{conferenceId}/{paperId}/evaluations` | `ADMIN`, `CHAIR`, `ASISTANT` o `ASSISTANT` |
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
  -H "Authorization: Bearer $TOKEN" \
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

1. Verificar JWT con rol `ADMIN` o `AUTHOR`.
2. Enviar `multipart/form-data` con parte `paper` como JSON y parte opcional
   `files`.
3. Confirmar que la respuesta incluye `documents` cuando se subieron archivos.
4. Si la respuesta es 500 durante la carga, revisar conectividad y permisos del
   bucket; la subida al bucket ocurre antes de guardar el registro JPA.

### Evaluacion de papers

Codigo principal: `PaperController.evaluate` -> `PaperService.evaluate`.

- Solo cambia `status` y `evaluationObservations`.
- `status` debe ser uno de `PaperStatus`.
- El paper se busca por `paperId` y `conferenceId`; si no coinciden devuelve 404.

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

## Troubleshooting

- **La aplicacion no arranca por propiedades faltantes:** revisar
  `SPRING_DATASOURCE_URL`, `FRONTEND_URL`, `JWT_PUBLIC_KEY` y variables `BACKBLAZE_*`.
- **JWT valido pero sin permisos:** verificar que el claim use `roles`, `role` o
  `authority` y que contenga roles como `ADMIN`, `AUTHOR`, `CHAIR`, `ASSISTANT` o
  `ASISTANT`.
- **CORS bloquea el frontend:** confirmar que `FRONTEND_URL` coincide con el origen
  real del navegador. Se admiten patrones porque se usa `setAllowedOriginPatterns`.
- **Descargas devuelven 404:** confirmar que el ID pertenezca a la misma conferencia
  y paper cuando aplica; los servicios filtran por esos IDs antes de descargar.
- **Archivos no se eliminan del bucket:** la eliminacion requiere que el cliente S3
  use path-style access, habilitado en `BackblazeConfig`.
- **Extensiones inesperadas:** si el optimizador cambia el tipo MIME, la clave del
  objeto usa extension compatible con el contenido optimizado.

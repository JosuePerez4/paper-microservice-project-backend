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

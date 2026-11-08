# AirTortilla - Sistema de Gestión y Distribución de Reservas

Sistema backend y frontend desarrollado para la aerolínea **AirTortilla** que permite procesar archivos de reservas aéreas, filtrarlas y dividirlas automáticamente en ficheros independientes según el **país de Destino**, resolviendo además colisiones de pasajeros coincidentes mediante identificadores y localizadores inequívocos.

## Captura de AirTortilla

![Diagrama de Flujo AirTortilla](AirTortilla.drawio.png)

---

## Descripción y Reglas de Negocio

El sistema procesa archivos estructurados `.csv` de reservas cargados por el usuario y aplica las siguientes reglas de negocio:

### 1. Formato de Columnas del Archivo CSV de Entrada

El archivo `.csv` subido debe contener exactamente la siguiente cabecera:

```
id,nombre_pasajero,origen,destino,pais_destino,fecha_reserva,localizador,precio,asiento
```

- **id**: Identificador de la reserva (si viene vacío, el sistema genera uno único e inequívoco).
- **nombre_pasajero**: Nombre completo del pasajero.
- **origen**: Ciudad o aeropuerto de origen.
- **destino**: Ciudad o aeropuerto de destino.
- **pais_destino**: Código ISO de 2 caracteres del país de destino (ej. `ES`, `FR`, `DE`, `IT`, `PT`, `GB`, `US`).
- **fecha_reserva**: Fecha en formato ISO `yyyy-MM-dd`.
- **localizador**: Código localizador del billete (si viene vacío o colisiona, el sistema asigna uno único).
- **precio**: Importe de la reserva.
- **asiento**: Código de asiento asignado.

### 2. División Exclusiva por País de Destino

- **Un único archivo por país**: Todas las reservas con el mismo código ISO de país de destino se agrupan en un único fichero, **sin importar la ciudad concreta de destino ni la fecha individual del vuelo**.
  - _Ejemplo_: Si hay un vuelo a Berlín (`destino=Berlin, pais_destino=DE`) y otro a Múnich (`destino=Munich, pais_destino=DE`), **ambos se incluyen en el mismo archivo de Alemania**.
  - `XX`: Código ISO de 2 letras en mayúsculas del país de destino.
  - `YYYY_MM_DD`: Fecha de generación del fichero.
  - _Ejemplos_: `AirTortilla_DE_2026_10_13.csv`, `AirTortilla_FR_2026_10_13.csv`, `AirTortilla_IT_2026_10_13.csv`.

- **Ubicación**: Los ficheros se generan en la carpeta `archivos_generados/`.

### 3. Identificación Inequívoca y Desambiguación de Pasajeros

- En escenarios donde **2 o más pasajeros coinciden en Nombre, Origen y Destino**, el sistema detecta la colisión y garantiza un **localizador diferenciado y persistente** para cada uno (ej. `AT-DE-C1A`, `AT-DE-C2B`).
- Garantiza identificadores (`id`) y localizadores (`localizador`) únicos para evitar duplicidades o billetes ambiguos.

### 4. Modalidades de Consulta en la Interfaz Web

- **Modalidad Total**: Permite consultar el fichero completo de un país, alternando entre vista de tabla estructurada y vista del texto CSV en bruto (_raw_), con opción de descarga directa.
- **Modalidad Línea a Línea**: Permite inspeccionar el fichero registro a registro, con controles de navegación (Inicio, Anterior, Siguiente, Fin), control deslizante (_slider_), modo _Auto-Play_ con velocidad ajustable, ficha detallada del billete y vista de la línea sin procesar.

---

## Stack Tecnológico

- **Lenguaje**: Java 21 (LTS).
- **Framework Principal**: Spring Boot 3.4.3 (`spring-boot-starter-web` con servidor Tomcat embebido).
- **Persistencia y ORM**: Spring Data JPA con Hibernate (`spring-boot-starter-data-jpa`).
- **Base de Datos Relacional**: H2 Database (`com.h2database:h2`) configurada en modo archivo para persistencia de datos.
- **Librería de Parseo**: OpenCSV 5.9 (`CsvToBeanBuilder` y `CSVWriter`).
- **Gestor de Construcción**: Maven (a través del wrapper incluido `mvnw`).
- **Frontend**: HTML5 semántico, CSS3 nativo (diseño responsive temático de aviación) y JavaScript Vanilla (`Fetch API`).

---

## Base de Datos Relacional (H2 + Spring Data JPA)

El sistema integra una base de datos relacional mediante **Spring Data JPA (Hibernate)** y el motor **H2 Database**, lo que permite mantener persistencia de las reservas entre reinicios de la aplicación sin necesidad de instalar servicios o servidores externos.

### 1. Estructura de la Tabla `reservas`

La entidad `Reserva` está mapeada automáticamente a la tabla relacional `reservas`:

| Columna | Tipo SQL | Restricciones / Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id` | `VARCHAR(60)` | **PRIMARY KEY**, NOT NULL | Identificador único inequívoco de la reserva. |
| `nombre_pasajero` | `VARCHAR(255)` | NOT NULL | Nombre y apellidos del pasajero. |
| `origen` | `VARCHAR(255)` | NOT NULL | Ciudad / aeropuerto de origen. |
| `destino` | `VARCHAR(255)` | NOT NULL | Ciudad / aeropuerto de destino. |
| `pais_destino` | `VARCHAR(10)` | NOT NULL | Código ISO de 2 letras del país de destino (`ES`, `FR`, etc.). |
| `fecha_reserva` | `DATE` | NOT NULL | Fecha de la reserva (`yyyy-MM-dd`). |
| `localizador` | `VARCHAR(50)` | Nullable | Localizador único o desambiguado. |
| `precio` | `FLOAT(53)` | Nullable | Precio del billete. |
| `asiento` | `VARCHAR(20)` | Nullable | Asiento asignado. |

### 2. Acceso a la Consola Web de H2

Spring Boot expone una consola web interactiva para inspeccionar las tablas, registros y ejecutar consultas SQL directamente:

- **URL de la Consola**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **Parámetros de Conexión**:
  - **Saved Settings**: `Generic H2 (Embedded)`
  - **Driver Class**: `org.h2.Driver`
  - **JDBC URL**: `jdbc:h2:file:./data/airtortilla_db`
  - **User Name**: `sa`
  - **Password**: *(dejar en blanco)*

### 3. Consultas SQL de Ejemplo en la Consola

```sql
-- Consultar todas las reservas persistidas
SELECT * FROM reservas;

-- Consultar reservas hacia un país específico (ej. Italia)
SELECT id, nombre_pasajero, origen, destino, localizador 
FROM reservas 
WHERE pais_destino = 'IT';

-- Conteo de reservas agrupadas por país de destino
SELECT pais_destino, COUNT(*) AS total_reservas 
FROM reservas 
GROUP BY pais_destino 
ORDER BY total_reservas DESC;
```

### 4. Capa de Repositorio (`ReservaRepository`)

La persistencia se gestiona a través de la interfaz `ReservaRepository`, que extiende de `JpaRepository<Reserva, String>` e incluye métodos de consulta derivados:

```java
public interface ReservaRepository extends JpaRepository<Reserva, String> {
    List<Reserva> findByCodigoPaisDestino(String codigoPaisDestino);
    List<Reserva> findByNombrePasajeroContainingIgnoreCase(String nombrePasajero);
}
```

---

## Cómo Levantar el Proyecto

### 1. Requisitos Previos

- Tener instalado **JDK 21** configurado en el `PATH`.

### 2. Ejecución en Modo Desarrollo

Abre un terminal en la raíz del proyecto y ejecuta:

- **En Windows (PowerShell / CMD)**:

  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

- **En Linux / macOS**:
  ```bash
  ./mvnw spring-boot:run
  ```

### 3. Puertos y Acceso Web

- **Puerto utilizado**: `8080` (configurado en `src/main/resources/application.properties`).
- **URL de acceso**: Abre tu navegador web en:
  **[http://localhost:8080](http://localhost:8080)**

---

## Resumen de la API REST

| Método   | Endpoint                                           | Descripción                                                                                          |
| :------- | :------------------------------------------------- | :--------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/reservas`                                    | Devuelve el total de reservas cargadas, países únicos y análisis de coincidencias.                   |
| `POST`   | `/api/reservas/subir`                              | Sube un archivo CSV mediante `multipart/form-data` y genera automáticamente los ficheros por país.   |
| `POST`   | `/api/reservas/generar-archivos`                   | Regenera los archivos divididos por país a partir de las reservas activas.                           |
| `GET`    | `/api/reservas/archivos`                           | Lista los archivos generados con sus metadatos (tamaño, número de reservas, fecha).                  |
| `GET`    | `/api/reservas/archivos/{nombre}/total`            | Consulta un archivo en **Modalidad Total** (devuelve reservas estructuradas y texto CSV en bruto).   |
| `GET`    | `/api/reservas/archivos/{nombre}/linea?numero={n}` | Consulta un archivo en **Modalidad Línea a Línea** (devuelve el registro `n`, anterior y siguiente). |
| `GET`    | `/api/reservas/archivos/{nombre}/descargar`        | Descarga el archivo CSV físico generado.                                                             |
| `DELETE` | `/api/reservas/archivos`                           | Elimina todos los archivos generados en la carpeta `archivos_generados/`.                            |

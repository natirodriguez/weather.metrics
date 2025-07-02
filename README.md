# Proyecto Weather

## Requerimientos

- Java 21
- Maven 3.3.3 o superior
- MongoAtlas 8.0.8
- Prometheus
- Grafana
- JMeter

## Instalación

### Instalar Java en Windows

Se puede descargar la última versión de Java desde el sitio oficial de Oracle:

- [Descargar Java - Oracle](https://www.oracle.com/java/technologies/javase-downloads.html)

Instálela y recuerde el path dónde se instaló el JDK.
Luego configure las siguientes variables de entorno:

- **JAVA_HOME**
  - Crear una nueva variable de sistema:
    - **Nombre**: `JAVA_HOME`
    - **Valor**: la ruta donde instaló el JDK

- **Path**
  - Editar la variable existente `Path`.
  - Agregar al final:
    ```
    ;%JAVA_HOME%\bin
    ```

### Instalar Maven en Windows

Se puede descargar Maven desde el repositorio oficial de Apache:

- [Descargar Maven 3.3.3 - Apache Archive](https://archive.apache.org/dist/maven/maven-3/3.3.3/binaries/)

Descargue el archivo apache-maven-3.3.3-bin.zip y extraiga su contenido en un path definitivo.
Luego configure las siguientes variables de entorno:

- **MAVEN_HOME**
  - Crear nueva variable de sistema:
    - **Nombre**: `MAVEN_HOME`
    - **Valor**: la ruta donde copió Maven

- **Path**
  - Editar la variable existente `Path`.
  - Agregar al final:
    ```
    ;%MAVEN_HOME%\bin
    ```
### Instalar Prometheus en Windows
Bajar la version de [Prometheus](https://prometheus.io/download/)
Descomprimirlo
Agregarle esta configuracion en el archivo Prometheus.yml
```
scrape_configs:
  # The job name is added as a label `job=<job_name>` to any timeseries scraped from this config.
  - job_name: "weather-metrics"
    metrics_path: '/actuator/prometheus'
    # metrics_path defaults to '/metrics'
    # scheme defaults to 'http'.
    static_configs:
      - targets: ["localhost:8080"]
```
Sobre la carpeta de prometheus, abrir la consola y ejecutar: 
>prometheus.exe --config.file=prometheus.yml

Se puede verificar que funciona con la siguiente URL: http://localhost:8080/actuator/prometheus

### Instalar Grafana en Windows
Bajar la version de [Grafana](https://grafana.com/grafana/download)
Descomprimirlo y ejecutarlo
Sobre la carpeta bin de grafana, abrir la consola y ejecutar: 
>grafana.exe server

Se puede verificar que funciona con la siguiente URL: http://localhost:3000/dashboards

### Configurar MongoAtlas

Ve a [MongoDB Atlas](https://www.mongodb.com/cloud/atlas) y crea una cuenta si no tienes una, o inicia sesión si ya tienes una.

Crear un nuevo cluster:
  - Una vez dentro, selecciona el botón **"Create a New Cluster"**.
  - Elige tu proveedor de nube preferido (AWS, Google Cloud o Azure) y la región donde se desplegará tu cluster.
  - Selecciona el **tipo de cluster**.
  - Haz clic en **Create Cluster**.

Crear un usuario en MongoDB Atlas:
- Ve a la sección **Database Access**.
- Haz clic en **Add New Database User**.
- Crea un usuario con nombre y contraseña.

Obtener la URI de conexión:
- En el panel de MongoDB Atlas, ve a **Clusters** y haz clic en **Connect** para tu cluster.
- Elige **Connect your application**.
- Selecciona la versión de tu controlador de MongoDB para Java.
- Copia la **URI de conexión** y sustituye `<username>`, `<password>` y `<database>` con los valores correspondientes.

Configurar el archivo `application.properties` del proyecto:
- Abre el archivo `application.properties` del proyecto previamente descargado y modifica las siguientes properties:
```properties
spring.data.mongodb.uri=**URI de conexión** recientemente copiada
spring.data.mongodb.database=**Nombre de la database**
```

Abrir una consola CMD en donde se encuentra el codigo del proyecto descargado y ejecutar el siguiente comando:

```bash
mvn spring-boot:run
```

### Instalar JMeter y correr test de carga
Bajar la última versión de [JMeter](https://jmeter.apache.org/download_jmeter.cgi) y descomprimirlo. 
Dentro de la carpeta bin, es necesario buscar el archivo jmeter y ejecutarlo. 

Dentro de Jmeter, ir a "Open" para importar los .jmx y presionar run. 


## pbactoestafeta

Prueba de concepto para mostrar la descarga de múltiples archivos embebidos en un ZIP descargados de un blob storage de Azure (en paralelo). La prueba muestra los siguientes temas:

- Los archivos del blob storage se descargan en paralelo para reducir el tiempo total.
- Todos los archivos se agregan a un ZIP el cual se genera "al vuelo" de forma secuencial y sincronizada en lo que se van descargando los archivos del blob.
- Dado que el ZIP se genera "al vuelo" o en modo streaming, se empieza a regresar la respuesta al cliente de forma rápida, evitando el timeout que el load balancer de azure provoca si no se hay flujo de información por 230/240 segundos (https://learn.microsoft.com/en-us/troubleshoot/azure/app-service/web-apps-performance-faqs#why-does-my-request-time-out-after-230-seconds).

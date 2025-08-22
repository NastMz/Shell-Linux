# Shell Linux - Servidor

Aplicación servidor desarrollada en Java que recibe y ejecuta comandos del cliente Windows remotamente.

## Descripción

Servidor del proyecto de Sistemas Operativos que procesa comandos enviados desde el cliente Windows y ejecuta instrucciones en el sistema Linux.

## Tecnologías

- **Java**: ProcessBuilder para ejecución de comandos
- **Sockets**: Comunicación servidor-cliente
- **Threads**: Manejo concurrente de clientes

## Características

- Servidor multi-cliente
- Ejecución de comandos del sistema Linux
- Autenticación de usuarios
- Monitoreo de rendimiento del sistema

## Funcionalidades

- Recepción de comandos via socket
- Ejecución segura con ProcessBuilder
- Envío de resultados al cliente
- Monitoreo de CPU, RAM y almacenamiento

## Integración

Trabaja con el cliente: [Shell-Windows](https://github.com/NastMz/Shell-Windows)

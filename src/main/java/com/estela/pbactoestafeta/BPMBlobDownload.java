package com.estela.pbactoestafeta;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;

import jakarta.servlet.annotation.WebServlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import jakarta.servlet.ServletException;

@WebServlet(name = "bpmdownload", urlPatterns = {"/download"})
public class BPMBlobDownload extends HttpServlet {

    private static final String CONNECTION_STRING = System.getenv("CONNECTION_STRING");
    private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(CONNECTION_STRING)
                .buildClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"descarga-" + java.util.UUID.randomUUID().toString() + ".zip\"");

        int threadPoolSize = 2 * Runtime.getRuntime().availableProcessors();
        //administra el pool de hilos para ejecutar la descarga de cada uno de los blobs
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

        try (ServletOutputStream servletOutputStream = response.getOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(servletOutputStream)) {

            //En vez de la lista de blob items, en el programa real se tiene una lista de blobpaths, pero básicamente es lo mismo
            ListBlobsOptions options = new ListBlobsOptions();
            List<BlobItem> blobItems = containerClient.listBlobs(options, null)
                    .stream().collect(Collectors.toList());

            if (blobItems.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró ningún archivo a descargar");
                return;
            }

            //Este servicio va procesando los blobs conforme se completa la descarga de cada uno
            CompletionService<ZipEntryData> completionService = new ExecutorCompletionService<>(executorService);

            for (BlobItem blobItem : blobItems) {
                completionService.submit(() -> readBlobData(containerClient, blobItem.getName()));
            }

            int received = 0;
            while (received < blobItems.size()) {
                Future<ZipEntryData> future = completionService.take(); //Bloquea hasta que se complete una tarea
                received++;

                try {
                    ZipEntryData zipEntryData = future.get();

                    // Escribe secuencialmente en el stream de salida del zip
                    synchronized (zipOutputStream) {
                        zipOutputStream.putNextEntry(new ZipEntry(zipEntryData.getEntryName()));
                        try (InputStream inputStream = zipEntryData.getInputStream()) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                zipOutputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        zipOutputStream.closeEntry();
                    }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error al descargar los archivos: " + cause.getMessage());
                    return;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Proceso de descarga interrumpido: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }
    
    private ZipEntryData readBlobData(BlobContainerClient containerClient, String blobName) {
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        InputStream blobInputStream = blobClient.openInputStream();
        return new ZipEntryData(blobName, blobInputStream);
    }

    private static class ZipEntryData {
        private final String entryName;
        private final InputStream inputStream;

        public ZipEntryData(String entryName, InputStream inputStream) {
            this.entryName = entryName;
            this.inputStream = inputStream;
        }

        public String getEntryName() {
            return entryName;
        }

        public InputStream getInputStream() {
            return inputStream;
        }
    }
}

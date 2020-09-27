import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * HTTP Client API in Java 11
 *
 * To run these tests first run this docker:
 * <code>docker pull kennethreitz/httpbin</code>
 * <code>docker run -p 8080:80 kennethreitz/httpbin</code>
 */
public class TestNewHttpClient {

    public static final String BASE_URL = "http://localhost:8080";

    @Test
    public void testGetXMLSynchronousRequest() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/xml"))
                .setHeader("Accept", "application/xml")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        Assert.assertTrue(response.body().startsWith("<?xml"));

        HttpResponse<Stream<String>> streamResponse =
                client.send(request, HttpResponse.BodyHandlers.ofLines());
        Assert.assertEquals(24, streamResponse.body().count());
    }

    @Test
    public void testRequestHttp2NotSupported() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://goodtechlife.com/"))
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assert.assertEquals(HttpClient.Version.HTTP_1_1, response.version());
    }

    @Test
    public void testRequestRedirectNeverAndAlways() {
        Function<HttpClient, HttpResponse<Void>> requestAndGetResponse = httpClient -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/redirect-to?url=http%3A%2F%2Fgoodtechlife.com"))
                    .build();
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<?> response = requestAndGetResponse.apply(client);
        Assert.assertEquals(302, response.statusCode());


        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        response = requestAndGetResponse.apply(client);
        Assert.assertEquals(200, response.statusCode());

        //NORMAL HERE IS LIKE ALWAYS
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        response = requestAndGetResponse.apply(client);
        Assert.assertEquals(200, response.statusCode());
    }

    @Test
    public void testRequestRedirectNormal() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/redirect-to?url=https://duckduckgo.com"))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assert.assertEquals(500, response.statusCode());
    }

    @Test
    public void testBasicAuthenticationSuccessful() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .authenticator(new Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                "admin", "admin123".toCharArray());
                    }
                })
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/basic-auth/admin/admin123"))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assert.assertEquals(200, response.statusCode());
    }

    @Test
    public void testBasicAuthenticationNotSuccessful() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/basic-auth/admin/admin123"))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assert.assertEquals(401, response.statusCode());
    }

    @Test
    public void testPostFormEmptyBody() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(240))
                .uri(URI.create(BASE_URL + "/post"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> jsonResult = mapper.readValue(response.body(), Map.class);
        Map<?, ?> formData = (Map<?, ?>) jsonResult.get("form");

        Assert.assertTrue(formData.isEmpty());
    }

    @Test
    public void testPostFormWithBody() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(240))
                .uri(URI.create(BASE_URL + "/post"))
                .POST(HttpRequest.BodyPublishers.ofString("firstName=Ramin&lastName=Zare"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> jsonResult = mapper.readValue(response.body(), Map.class);
        Map<?, ?> formData = (Map<?, ?>) jsonResult.get("form");
        System.out.println(formData);

        Assert.assertEquals("Ramin", formData.get("firstName"));
        Assert.assertEquals("Zare", formData.get("lastName"));
    }

    @Test
    public void testAsyncDownload() throws ExecutionException, InterruptedException, IOException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/image/jpeg"))
                .build();

        Path tempFile = File.createTempFile("image", ".jpeg").toPath();
        CompletableFuture<HttpResponse<Path>> response = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofFile(tempFile));
        Path path = response.thenApply(HttpResponse::body).get();
        System.out.println(path.toAbsolutePath().toString());
        Assert.assertTrue(path.toFile().exists());
        Assert.assertTrue(path.toFile().length() > 0);
    }

    @Test
    public void testAsyncDownloadWithExecutor() throws ExecutionException, InterruptedException, IOException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        final HttpClient client = HttpClient.newBuilder().executor(executorService).build();

        final String[] urls = {
                BASE_URL + "/image/jpeg",
                BASE_URL + "/image/png",
                BASE_URL + "/image/svg",
                BASE_URL + "/image/webp"
        };

        List<CompletableFuture<Path>> resultList =
                Arrays.stream(urls).map(url -> HttpRequest.newBuilder().uri(URI.create(url)).build())
                        .map(httpRequest -> {
                            try {
                                Path tempFile = File.createTempFile("image", ".tmp").toPath();
                                return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofFile(tempFile))
                                        .thenApply(response -> {
                                            System.out.println(Thread.currentThread().getName());
                                            return response.body();
                                        });
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .collect(Collectors.toList());


        for (CompletableFuture<Path> futurePath : resultList) {
            System.out.println(futurePath.get().toString());
            Path path = futurePath.get();
            Assert.assertTrue(path.toFile().exists());
            Assert.assertTrue(path.toFile().length() > 0);
        }
    }
}
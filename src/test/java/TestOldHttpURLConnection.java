import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * To run these tests first run this docker:
 * <code>docker pull kennethreitz/httpbin</code>
 * <code>docker run -p 8080:80 kennethreitz/httpbin</code>
 */
public class TestOldHttpURLConnection {
    @Test
    public void testOldHttpURLConnection() throws IOException {
        String url = "http://localhost:8080/xml";
        URL urlObj = new URL(url);
        HttpURLConnection urlCon = (HttpURLConnection) urlObj.openConnection();
        InputStream inputStream = urlCon.getInputStream();
        String result;

        Assert.assertEquals(HttpURLConnection.HTTP_OK ,urlCon.getResponseCode());
        try(BufferedInputStream reader = new BufferedInputStream(inputStream);
            ByteArrayOutputStream bytes =  new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = reader.read(buffer)) != -1) {
                bytes.write(buffer, 0, bytesRead);
            }
           result = bytes.toString(StandardCharsets.UTF_8);
        }
        //System.out.println(result);
        Assert.assertTrue(result.startsWith("<?xml"));
    }
}

package io.codegaze.ide;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class HttpServiceTest extends BasePlatformTestCase {
    public void testAuthenticatedLifecycleAndSessionGuard() throws Exception {
        GazeService service = new GazeService(getProject());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        service.start();
        try {
            HttpRequest unauth = HttpRequest.newBuilder(URI.create(service.url()+"/api/status")).GET().build();
            assertEquals(401,client.send(unauth,HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpRequest foreign = HttpRequest.newBuilder(URI.create(service.url()+"/api/status"))
                    .header("Authorization","Bearer "+service.token()).header("Origin","https://example.com").GET().build();
            assertEquals(403,client.send(foreign,HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpResponse<String> started = send(client,service,"/api/session/start","{\"participant\":\"test-only\"}");
            assertEquals(200,started.statusCode());
            String id = JsonParser.parseString(started.body()).getAsJsonObject().get("sessionId").getAsString();
            String sample = "{\"clientId\":\"http-test\",\"sequence\":0,\"frameId\":999,\"clientMonoMs\":1,\"source\":\"head\",\"valid\":true,\"u\":0.5,\"v\":0.5}";
            assertEquals(409,send(client,service,"/api/samples","{\"sessionId\":\"old-session\",\"samples\":["+sample+"]}").statusCode());
            HttpResponse<String> accepted = send(client,service,"/api/samples","{\"sessionId\":\""+id+"\",\"samples\":["+sample+"]}");
            assertEquals(200,accepted.statusCode());
            assertTrue(accepted.body().contains("frame_expired"));
            assertEquals(200,send(client,service,"/api/session/stop","{}").statusCode());
            HttpResponse<byte[]> exported = client.send(HttpRequest.newBuilder(URI.create(service.url()+"/api/export"))
                    .header("Authorization","Bearer "+service.token()).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,exported.statusCode());assertTrue(exported.body().length>100);
        } finally { service.dispose(); }
    }
    private static HttpResponse<String> send(HttpClient client,GazeService service,String path,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(service.url()+path)).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+service.token()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}

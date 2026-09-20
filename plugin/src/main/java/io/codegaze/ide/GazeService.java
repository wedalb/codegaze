package io.codegaze.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.PathManager;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import io.codegaze.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

@Service(Service.Level.PROJECT)
public final class GazeService implements Disposable {
    public static final int PORT = 8742;
    private final Project project;
    private final EditorCapture capture;
    private final FrameStore store = new FrameStore(120);
    private final Recorder recorder;
    private final String token;
    private HttpServer server;
    private ExecutorService executor;
    private volatile Model.Frame latest;
    private volatile String error;
    private volatile long nextCapture;
    private volatile int port = PORT;

    public GazeService(Project project) {
        this.project = project; this.capture = new EditorCapture(project);
        byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        recorder = new Recorder(Path.of(PathManager.getSystemPath(), "codegaze-recordings"));
    }
    public synchronized void start() throws IOException {
        if (server != null) return;
        HttpServer candidate = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        executor = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "CodeGaze HTTP"); t.setDaemon(true); return t; });
        candidate.setExecutor(executor); candidate.createContext("/", this::handle);
        candidate.start(); server = candidate; port = candidate.getAddress().getPort(); error = null;
    }
    public synchronized void stop() {
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
        try { recorder.stop(); } catch (IOException e) { error = e.getMessage(); }
        latest = null; store.clear();
    }
    public boolean running() { return server != null; }
    public String token() { return token; }
    public String url() { return "http://127.0.0.1:" + port; }
    public String viewerUrl() { return url() + "/#key=" + token; }
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>(recorder.status());
        out.put("version", "0.1.0"); out.put("protocol", 1); out.put("captureError", error);
        out.put("frameId", latest == null ? null : latest.id()); out.put("demo", false);
        return out;
    }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (host == null || !(host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port))) {
                respond(exchange, 403, Map.of("error", "Invalid Host")); return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !(origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port))) {
                respond(exchange, 403, Map.of("error", "Cross-origin access is disabled")); return;
            }
            if (!path.startsWith("/api/")) { asset(exchange, path); return; }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), auth.getBytes(StandardCharsets.UTF_8))) {
                respond(exchange, 401, Map.of("error", "Enter the pairing key shown in the CodeGaze tool window")); return;
            }
            String method = exchange.getRequestMethod();
            if (path.equals("/api/status") && method.equals("GET")) respond(exchange, 200, status());
            else if (path.equals("/api/frame") && method.equals("GET")) {
                Model.Frame frame;
                synchronized (capture) {
                    if (latest == null || System.nanoTime() >= nextCapture) {
                        latest = capture.capture(); store.add(latest); nextCapture = System.nanoTime() + 180_000_000L;
                    }
                    frame = latest;
                }
                respond(exchange, 200, frame);
            } else if (path.equals("/api/session/start") && method.equals("POST")) {
                JsonObject input = parse(exchange);
                String participant = input.has("participant") ? input.get("participant").getAsString() : "anonymous";
                if (participant.length() > 128) throw new IllegalArgumentException("Participant label is too long");
                recorder.start(Map.of("participant", participant, "software", "CodeGaze 0.1.0", "protocol", 1,
                        "mapping", "measured-point estimate; no calibrated uncertainty model",
                        "capture", "active IntelliJ editor; Swing logical pixels",
                        "clock", "client software sampling time and server receipt time; not sensor acquisition time"));
                respond(exchange, 200, status());
            } else if (path.equals("/api/session/stop") && method.equals("POST")) {
                recorder.stop(); respond(exchange, 200, status());
            } else if (path.equals("/api/samples") && method.equals("POST")) {
                JsonObject input = parse(exchange);
                if (!input.has("sessionId") || !input.get("sessionId").getAsString().equals(recorder.status().get("sessionId")))
                    throw new IllegalStateException("Session changed; refresh status before sending samples");
                JsonArray samples = input.getAsJsonArray("samples");
                if (samples == null || samples.size() > 256) throw new IllegalArgumentException("Expected at most 256 samples");
                List<Model.Sample> validated = new ArrayList<>();
                for (JsonElement sample : samples) { Model.Sample s = Recorder.JSON.fromJson(sample, Model.Sample.class); Mapper.validate(s); validated.add(s); }
                Model.Event last = null;
                for (Model.Sample sample : validated) last = recorder.record(sample, store.get(sample.frameId()));
                Map<String,Object> result = new LinkedHashMap<>(); result.put("accepted", validated.size()); result.put("last", last);
                respond(exchange, 200, result);
            } else if (path.equals("/api/export") && method.equals("GET")) {
                byte[] zip = recorder.export(); exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=codegaze-session.zip");
                send(exchange, 200, "application/zip", zip);
            } else respond(exchange, 404, Map.of("error", "Unknown endpoint or method"));
        } catch (IllegalArgumentException | JsonParseException e) { respond(exchange, 400, Map.of("error", safe(e))); }
        catch (IllegalStateException e) { respond(exchange, 409, Map.of("error", safe(e))); }
        catch (Exception e) { error = safe(e); respond(exchange, 500, Map.of("error", error)); }
        finally { exchange.close(); }
    }
    private static String safe(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static JsonObject parse(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(256 * 1024 + 1);
        if (bytes.length > 256 * 1024) throw new IllegalArgumentException("Request too large");
        JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!element.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
        return element.getAsJsonObject();
    }
    private static void respond(HttpExchange ex, int code, Object value) throws IOException {
        send(ex, code, "application/json; charset=utf-8", Recorder.JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
    private static void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(code, data.length); ex.getResponseBody().write(data);
    }
    private void asset(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { respond(ex, 405, Map.of("error", "GET required")); return; }
        if (path.equals("/")) path = "/index.html";
        if (!Set.of("/index.html", "/app.mjs", "/core.mjs", "/xr.mjs", "/style.css").contains(path)) { respond(ex,404,Map.of("error","Not found")); return; }
        try (InputStream input = getClass().getResourceAsStream("/web" + path)) {
            if (input == null) { respond(ex,404,Map.of("error","Viewer asset missing")); return; }
            String type = path.endsWith(".html") ? "text/html" : path.endsWith(".css") ? "text/css" : "text/javascript";
            ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob:; connect-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'");
            send(ex,200,type + "; charset=utf-8",input.readAllBytes());
        }
    }
    @Override public void dispose() { stop(); }
}

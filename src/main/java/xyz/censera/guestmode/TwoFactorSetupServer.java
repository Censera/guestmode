package xyz.censera.guestmode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class TwoFactorSetupServer {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GuestMode plugin;
    private HttpServer server;
    private Setup setup;
    private long expiryMs;

    TwoFactorSetupServer(GuestMode plugin) {
        this.plugin = plugin;
    }

    synchronized String start(PlayerSetup playerSetup) throws IOException {
        stop();

        String host = plugin.getPluginConfig().getTwoFactorWebHost();
        int configuredPort = plugin.getPluginConfig().getTwoFactorWebPort();
        expiryMs = TimeUnit.SECONDS.toMillis(plugin.getPluginConfig().getTwoFactorWebExpirySeconds());
        if (configuredPort < 0 || configuredPort > 65535 || expiryMs <= 0) {
            throw new IOException("Invalid 2FA web configuration");
        }

        server = HttpServer.create(new InetSocketAddress(host, configuredPort), 0);
        setup = new Setup(playerSetup.uuid(), playerSetup.name(), playerSetup.secret(), playerSetup.uri(), token());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/2fa", this::handle);
        server.start();

        plugin.getServer().getScheduler().runTaskLater(plugin, this::expire, Math.max(1L, expiryMs / 50L));
        return "http://" + formatHost(host) + ":" + server.getAddress().getPort() + "/2fa/" + setup.token;
    }

    synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        setup = null;
    }

    private synchronized void expire() {
        if (setup != null && System.currentTimeMillis() - setup.createdAt >= expiryMs) {
            stop();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String prefix = "/2fa/";
        String path = exchange.getRequestURI().getPath();
        Setup current;
        synchronized (this) {
            current = setup;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET");
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }

        if (current == null || !path.startsWith(prefix) || !current.token.equals(path.substring(prefix.length()))
                || System.currentTimeMillis() - current.createdAt >= expiryMs) {
            send(exchange, 404, "Setup link expired", "text/plain; charset=utf-8");
            return;
        }

        send(exchange, 200, page(current), "text/html; charset=utf-8");
    }

    private static String page(Setup setup) throws IOException {
        String qr = qrSvg(setup.uri);
        String name = escape(setup.name);
        String secret = escape(setup.secret);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>GuestMode 2FA</title>"
                + "<style>body{font-family:system-ui,sans-serif;max-width:520px;margin:40px auto;padding:24px;color:#222}svg{display:block;width:320px;height:320px;margin:24px auto;background:#fff}code{user-select:all;word-break:break-all}button{padding:10px 14px;cursor:pointer}</style></head><body>"
                + "<h1>GuestMode 2FA</h1><p>Account: <strong>" + name + "</strong></p>"
                + "<p>Scan this QR code with your authenticator app.</p>" + qr
                + "<p>Setup key:</p><p><code id=\"secret\">" + secret + "</code></p>"
                + "<button onclick=\"navigator.clipboard.writeText(document.getElementById('secret').textContent)\">Copy setup key</button>"
                + "<p>After adding the account, return to Minecraft and run <code>/2fa confirm &lt;code&gt;</code>.</p>"
                + "<p>This setup page expires automatically.</p></body></html>";
    }

    private static String qrSvg(String uri) throws IOException {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, 37, 37, hints);
            StringBuilder cells = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        cells.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"1\" height=\"1\"/>");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 37 37\" shape-rendering=\"crispEdges\"><rect width=\"37\" height=\"37\" fill=\"white\"/><g fill=\"black\">" + cells + "</g></svg>";
        } catch (Exception e) {
            throw new IOException("Could not generate QR code", e);
        }
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String formatHost(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String token() {
        byte[] value = new byte[24];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    record PlayerSetup(UUID uuid, String name, String secret, String uri) { }

    private record Setup(UUID uuid, String name, String secret, String uri, String token, long createdAt) {
        Setup(UUID uuid, String name, String secret, String uri, String token) {
            this(uuid, name, secret, uri, token, System.currentTimeMillis());
        }
    }
}

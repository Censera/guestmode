package xyz.censera.guestmode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class TwoFactorSetupServer {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long DEFAULT_EXPIRY_MS = TimeUnit.MINUTES.toMillis(5);

    private final GuestMode plugin;
    private HttpServer server;
    private String publicHost;
    private int port;
    private long expiryMs = DEFAULT_EXPIRY_MS;
    private Setup setup;

    TwoFactorSetupServer(GuestMode plugin) {
        this.plugin = plugin;
    }

    synchronized String start(PlayerSetup playerSetup) throws IOException {
        stop();

        String host = plugin.getPluginConfig().getTwoFactorWebHost();
        int configuredPort = plugin.getPluginConfig().getTwoFactorWebPort();
        expiryMs = TimeUnit.SECONDS.toMillis(plugin.getPluginConfig().getTwoFactorWebExpirySeconds());

        if (configuredPort < 0 || configuredPort > 65535) {
            throw new IOException("Invalid 2FA web port: " + configuredPort);
        }
        if (expiryMs <= 0) {
            throw new IOException("Invalid 2FA web expiry: " + expiryMs);
        }

        InetSocketAddress address = new InetSocketAddress(host, configuredPort);
        server = HttpServer.create(address, 0);
        publicHost = host;
        port = server.getAddress().getPort();
        setup = new Setup(playerSetup.uuid(), playerSetup.name(), playerSetup.secret(), playerSetup.uri(), token());

        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/2fa", this::handle);
        server.start();

        plugin.getServer().getScheduler().runTaskLater(plugin, this::expire, expiryMs / 50L);
        return "http://" + formatHost(publicHost) + ":" + port + "/2fa/" + setup.token;
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
        try (exchange) {
            String requestPath = exchange.getRequestURI().getPath();
            String prefix = "/2fa/";
            if (!requestPath.startsWith(prefix)) {
                send(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }

            String token = requestPath.substring(prefix.length());
            Setup current;
            synchronized (this) {
                current = setup;
                if (current == null || !current.token.equals(token)
                        || System.currentTimeMillis() - current.createdAt >= expiryMs) {
                    send(exchange, 404, "Setup link expired", "text/plain; charset=utf-8");
                    return;
                }
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
                return;
            }

            byte[] qr = qrPng(current.uri);
            String page = page(current, qr);
            send(exchange, 200, page, "text/html; charset=utf-8");
        }
    }

    private static byte[] qrPng(String uri) throws IOException {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, 320, 320, hints);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(320, 320, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 320; y++) {
                for (int x = 0; x < 320; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            try (var out = new java.io.ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(image, "PNG", out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IOException("Could not generate QR code", e);
        }
    }

    private static String page(Setup setup, byte[] qr) {
        String qrData = "data:image/png;base64," + Base64.getEncoder().encodeToString(qr);
        String name = escape(setup.name);
        String secret = escape(setup.secret);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>GuestMode 2FA</title>"
                + "<style>body{font-family:system-ui,sans-serif;max-width:520px;margin:40px auto;padding:24px;color:#222}img{display:block;width:320px;height:320px;margin:24px auto}code{user-select:all;word-break:break-all}button{padding:10px 14px;cursor:pointer}</style></head><body>"
                + "<h1>GuestMode 2FA</h1><p>Account: <strong>" + name + "</strong></p>"
                + "<p>Scan this QR code with your authenticator app.</p><img src=\"" + qrData + "\" alt=\"2FA QR code\">"
                + "<p>Setup key:</p><p><code id=\"secret\">" + secret + "</code></p>"
                + "<button onclick=\"navigator.clipboard.writeText(document.getElementById('secret').textContent)\">Copy setup key</button>"
                + "<p>After adding the account, return to Minecraft and run <code>/2fa confirm &lt;code&gt;</code>.</p>"
                + "<p>This page expires automatically.</p></body></html>";
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String formatHost(String host) {
        if (host.contains(":")) {
            return "[" + host + "]";
        }
        return host;
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

    private static final class Setup {
        final UUID uuid;
        final String name;
        final String secret;
        final String uri;
        final String token;
        final long createdAt = System.currentTimeMillis();

        Setup(UUID uuid, String name, String secret, String uri, String token) {
            this.uuid = uuid;
            this.name = name;
            this.secret = secret;
            this.uri = uri;
            this.token = token;
        }
    }
}

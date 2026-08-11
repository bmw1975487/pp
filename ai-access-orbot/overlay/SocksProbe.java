package org.torproject.android;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SocksProbe {
    private SocksProbe() {}

    public static final class Result {
        public final String host;
        public final int httpCode;
        public final long durationMs;
        public final String firstLine;
        public final String body;
        public final String error;

        Result(String host, int httpCode, long durationMs, String firstLine, String body, String error) {
            this.host = host;
            this.httpCode = httpCode;
            this.durationMs = durationMs;
            this.firstLine = firstLine == null ? "" : firstLine;
            this.body = body == null ? "" : body;
            this.error = error == null ? "" : error;
        }

        public boolean okHttp() { return httpCode > 0; }
        public String summary() {
            if (okHttp()) return host + " HTTP " + httpCode + " " + durationMs + "ms";
            return host + " ERROR " + error + " " + durationMs + "ms";
        }
    }

    public static Result fetch(int socksPort, String host, String path, int timeoutMs, int bodyLimit) {
        long start = SystemClock.elapsedRealtime();
        Socket raw = null;
        SSLSocket ssl = null;
        try {
            raw = new Socket();
            raw.connect(new InetSocketAddress("127.0.0.1", socksPort), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            socks5Connect(raw, host, 443);

            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) sf.createSocket(raw, host, 443, true);
            SSLParameters p = ssl.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(p);
            ssl.setSoTimeout(timeoutMs);
            ssl.startHandshake();

            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(ssl.getOutputStream(), StandardCharsets.UTF_8));
            w.write("GET " + path + " HTTP/1.1\r\n");
            w.write("Host: " + host + "\r\n");
            w.write("User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 AI-Access-One/0.2\r\n");
            w.write("Accept: */*\r\n");
            w.write("Connection: close\r\n\r\n");
            w.flush();

            BufferedReader r = new BufferedReader(new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
            String status = r.readLine();
            int code = parseStatus(status);
            String line;
            while ((line = r.readLine()) != null && !line.isEmpty()) {}
            StringBuilder body = new StringBuilder();
            while ((line = r.readLine()) != null && body.length() < bodyLimit) {
                if (body.length() > 0) body.append('\n');
                body.append(line);
            }
            return new Result(host, code, SystemClock.elapsedRealtime() - start, status, body.toString(), "");
        } catch (Throwable t) {
            return new Result(host, -1, SystemClock.elapsedRealtime() - start, "", "",
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        } finally {
            try { if (ssl != null) ssl.close(); } catch (Throwable ignored) {}
            try { if (raw != null) raw.close(); } catch (Throwable ignored) {}
        }
    }

    private static void socks5Connect(Socket socket, String host, int port) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        out.write(new byte[]{0x05, 0x01, 0x00});
        out.flush();
        byte[] greeting = readExactly(in, 2);
        if (greeting[0] != 0x05 || greeting[1] != 0x00) {
            throw new IOException("SOCKS greeting rejected: " + (greeting[1] & 0xff));
        }

        byte[] domain = host.getBytes(StandardCharsets.UTF_8);
        if (domain.length > 255) throw new IOException("Host too long");
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        req.write(0x05); req.write(0x01); req.write(0x00); req.write(0x03);
        req.write(domain.length); req.write(domain, 0, domain.length);
        req.write((port >>> 8) & 0xff); req.write(port & 0xff);
        out.write(req.toByteArray());
        out.flush();

        byte[] head = readExactly(in, 4);
        if (head[0] != 0x05) throw new IOException("Invalid SOCKS version");
        if (head[1] != 0x00) throw new IOException("SOCKS CONNECT failed rep=" + (head[1] & 0xff));
        int atyp = head[3] & 0xff;
        if (atyp == 0x01) readExactly(in, 4);
        else if (atyp == 0x04) readExactly(in, 16);
        else if (atyp == 0x03) {
            int len = readExactly(in, 1)[0] & 0xff;
            readExactly(in, len);
        } else throw new IOException("Unknown SOCKS atyp=" + atyp);
        readExactly(in, 2);
    }

    private static byte[] readExactly(InputStream in, int count) throws IOException {
        byte[] b = new byte[count];
        int off = 0;
        while (off < count) {
            int n = in.read(b, off, count - off);
            if (n < 0) throw new IOException("Unexpected EOF");
            off += n;
        }
        return b;
    }

    private static int parseStatus(String s) {
        if (s == null) return -1;
        String[] p = s.split(" ");
        if (p.length < 2) return -1;
        try { return Integer.parseInt(p[1]); } catch (Throwable t) { return -1; }
    }

    public static String traceValue(String body, String key) {
        if (body == null) return "";
        String prefix = key + "=";
        for (String line : body.split("\\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }
}

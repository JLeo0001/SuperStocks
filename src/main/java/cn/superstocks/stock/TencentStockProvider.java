package cn.superstocks.stock;

import cn.superstocks.model.StockQuote;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TencentStockProvider implements StockProvider {
    private final HttpClient client;
    private final Duration timeout;
    private final String endpoint;
    private final Charset encoding;
    private final String separator;
    private final int maxSymbolsPerRequest;
    private final String userAgent;

    public TencentStockProvider(ConfigurationSection config) {
        int timeoutSeconds = config.getInt("timeout-seconds", 8);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.endpoint = config.getString("tencent.endpoint", "https://qt.gtimg.cn/q={symbols}");
        this.encoding = Charset.forName(config.getString("tencent.encoding", "GBK"));
        this.separator = config.getString("tencent.symbol-separator", ",");
        this.maxSymbolsPerRequest = Math.max(1, config.getInt("tencent.max-symbols-per-request", 80));
        this.userAgent = config.getString("tencent.user-agent", "SuperStocks/1.0");
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Map<String, StockQuote> fetch(Collection<String> symbols, Map<String, String> symbolMarkets) throws IOException, InterruptedException {
        Map<String, StockQuote> quotes = new HashMap<>();
        if (symbols.isEmpty()) {
            return quotes;
        }
        List<String> ordered = new ArrayList<>(symbols);
        for (int start = 0; start < ordered.size(); start += maxSymbolsPerRequest) {
            int end = Math.min(start + maxSymbolsPerRequest, ordered.size());
            quotes.putAll(fetchBatch(ordered.subList(start, end), symbolMarkets));
        }
        return quotes;
    }

    private Map<String, StockQuote> fetchBatch(List<String> symbols, Map<String, String> symbolMarkets) throws IOException, InterruptedException {
        Map<String, StockQuote> quotes = new HashMap<>();
        String joined = String.join(separator, symbols);
        String url = endpoint.replace("{symbols}", URLEncoder.encode(joined, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .header("User-Agent", userAgent)
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Tencent quote request failed: HTTP " + response.statusCode());
        }
        String body = new String(response.body(), encoding);
        for (String line : body.split(";")) {
            StockQuote quote = parseLine(line, symbolMarkets);
            if (quote != null && quote.valid()) {
                quotes.put(quote.symbol(), quote);
            }
        }
        return quotes;
    }

    private StockQuote parseLine(String line, Map<String, String> symbolMarkets) {
        int nameStart = line.indexOf("v_");
        int eq = line.indexOf('=');
        int firstQuote = line.indexOf('"');
        int lastQuote = line.lastIndexOf('"');
        if (nameStart < 0 || eq < 0 || firstQuote < 0 || lastQuote <= firstQuote) {
            return null;
        }
        String symbol = line.substring(nameStart + 2, eq).trim();
        String payload = line.substring(firstQuote + 1, lastQuote);
        String[] fields = payload.split("~", -1);
        if (fields.length < 33) {
            return null;
        }
        String name = value(fields, 1, symbol);
        double price = parseDouble(value(fields, 3, "0"));
        double previousClose = parseDouble(value(fields, 4, "0"));
        double change = fields.length > 31 ? parseDouble(value(fields, 31, "0")) : price - previousClose;
        double percent = fields.length > 32 ? parseDouble(value(fields, 32, "0")) : percentage(change, previousClose);
        String market = symbolMarkets.getOrDefault(symbol, "unknown");
        return new StockQuote(symbol, name, market, price, change, percent, Instant.now());
    }

    private static String value(String[] fields, int index, String fallback) {
        if (index >= fields.length || fields[index] == null || fields[index].isBlank()) {
            return fallback;
        }
        return fields[index];
    }

    private static double parseDouble(String input) {
        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException ex) {
            return 0.0D;
        }
    }

    private static double percentage(double change, double base) {
        if (base == 0.0D) {
            return 0.0D;
        }
        return change / base * 100.0D;
    }
}

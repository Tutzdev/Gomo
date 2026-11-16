package br.com.supermercados.prices.catalog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.supermercados.prices.collection.PublicCatalogHttp;

@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    static final List<Integer> WIDTHS = List.of(96, 160, 240, 480);
    private static final long MAX_ORIGINAL_BYTES = 5L * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private final Set<String> hosts;
    private final Path directory;

    public ProductImageService(
        @Value("${app.images.resize-hosts:phygital-files.mercafacil.com}") String hosts,
        @Value("${app.images.cache-directory:.local/image-cache}") Path directory) {

        this.hosts = Arrays.stream(hosts.split(",")).map(String::strip).filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());

        this.directory = directory;
    }

    public boolean accepts(URI source) {
        return "https".equalsIgnoreCase(source.getScheme()) && source.getHost() != null
                && hosts.contains(source.getHost().toLowerCase(Locale.ROOT)) && source.getUserInfo() == null
                && (source.getPort() == -1 || source.getPort() == 443);
    }

    static int snap(int requested) {
        return WIDTHS.stream().filter(width -> width >= requested).findFirst().orElse(WIDTHS.getLast());
    }

    public Optional<byte[]> thumbnail(URI source, int width) {
        if (!accepts(source)) {throw new IllegalArgumentException("host not allowed");}

        Path cached = directory.resolve(key(source) + "-" + width + ".jpg");

        try {
            if (Files.exists(cached)) {return Optional.of(Files.readAllBytes(cached));}

            Optional<BufferedImage> original = download(source);

            if (original.isEmpty()) {return Optional.empty();}

            byte[] jpeg = jpeg(scale(original.get(), width));
            Files.createDirectories(directory);
            Path partial = Files.createTempFile(directory, "thumb-", ".partial");
            Files.write(partial, jpeg);
            Files.move(partial, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            return Optional.of(jpeg);
        } catch (IOException exception) {
            log.warn("Product photo not resized ({}): {}", source.getHost(), exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> download(URI source) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(15))
                .header("User-Agent", PublicCatalogHttp.USER_AGENT).GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {return Optional.empty();}

            byte[] bytes = body.readNBytes((int) MAX_ORIGINAL_BYTES + 1);

            if (bytes.length > MAX_ORIGINAL_BYTES) {return Optional.empty();}

            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(bytes)));
        }
    }

    static BufferedImage scale(BufferedImage original, int width) {
        int targetWidth = Math.min(width, original.getWidth());
        int targetHeight = Math.max(1, Math.round(original.getHeight() * (targetWidth / (float) original.getWidth())));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();

        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.drawImage(
                original,
                0,
                0,
                targetWidth,
                targetHeight,
                null);
        } finally {
            graphics.dispose();
        }

        return scaled;
    }

    static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(0.82f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }

        return bytes.toByteArray();
    }

    private static String key(URI source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

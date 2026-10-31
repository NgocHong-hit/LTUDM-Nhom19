package common;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;

public class FileUtil {

    // Chuyển file ảnh ở Client thành chuỗi mã hóa Base64 để gửi qua Socket
    public static String convertFileToBase64(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static boolean isBase64Image(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            byte[] imageBytes = Base64.getDecoder().decode(value);
            return detectImageFormat(imageBytes) != null
                    && ImageIO.read(new ByteArrayInputStream(imageBytes)) != null;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    // Server nhận chuỗi Base64 và lưu ảnh với tên file an toàn.
    public static String saveBase64Image(String base64Data) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            String format = detectImageFormat(imageBytes);
            if (format == null || ImageIO.read(new ByteArrayInputStream(imageBytes)) == null) {
                return null;
            }

            File dir = new File("image");
            Files.createDirectories(dir.toPath());
            String fileName = UUID.randomUUID() + "." + format;
            File targetFile = new File(dir, fileName);
            Files.write(targetFile.toPath(), imageBytes);
            return "image/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String imageFileAsDataUri(String imagePath) throws IOException {
        File imageFile = new File(imagePath);
        if (!imageFile.isAbsolute() && !imageFile.isFile()) {
            imageFile = new File(System.getProperty("user.dir"), imagePath);
        }
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String format = detectImageFormat(imageBytes);
        if (format == null) {
            throw new IOException("Unsupported image format: " + imagePath);
        }
        String mimeType = "jpg".equals(format) ? "image/jpeg" : "image/" + format;
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private static String detectImageFormat(byte[] imageBytes) {
        if (imageBytes.length >= 8
                && (imageBytes[0] & 0xff) == 0x89
                && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4e
                && imageBytes[3] == 0x47) {
            return "png";
        }
        if (imageBytes.length >= 3
                && (imageBytes[0] & 0xff) == 0xff
                && (imageBytes[1] & 0xff) == 0xd8
                && (imageBytes[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (imageBytes.length >= 6) {
            String gifSignature = new String(imageBytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(gifSignature) || "GIF89a".equals(gifSignature)) {
                return "gif";
            }
        }
        if (imageBytes.length >= 2 && imageBytes[0] == 'B' && imageBytes[1] == 'M') {
            return "bmp";
        }
        return null;
    }
}
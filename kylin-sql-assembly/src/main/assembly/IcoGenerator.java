import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Build-time tool: read kylin_512x512.png, scale to 16/32/48/64, output kylin.ico.
 * Usage: java IcoGenerator <input.png> <output.ico>
 */
public class IcoGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java IcoGenerator <input.png> <output.ico>");
            System.exit(1);
        }
        BufferedImage src = ImageIO.read(new File(args[0]));
        if (src == null) {
            System.err.println("Cannot read: " + args[0]);
            System.exit(1);
        }
        int[] sizes = {16, 32, 48, 64};
        byte[][] pngData = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            BufferedImage scaled = new BufferedImage(sizes[i], sizes[i], BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, sizes[i], sizes[i], null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            pngData[i] = baos.toByteArray();
        }
        writeIco(new File(args[1]), pngData);
        System.out.println("Generated: " + args[1] + " (" + sizes.length + " sizes)");
    }

    static void writeIco(File file, byte[][] images) throws IOException {
        int count = images.length;
        int headerSize = 6 + count * 16;
        int offset = headerSize;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            // ICO header
            writeLE(out, 2, 0);     // reserved
            writeLE(out, 2, 1);     // type = ICO
            writeLE(out, 2, count); // image count
            // Directory entries + image data
            for (int i = 0; i < count; i++) {
                int w = getPngWidth(images[i]);
                int h = getPngHeight(images[i]);
                out.write(w >= 256 ? 0 : w);    // width
                out.write(h >= 256 ? 0 : h);    // height
                out.write(0);  // palette colors
                out.write(0);  // reserved
                writeLE(out, 2, 1);      // color planes
                writeLE(out, 2, 32);     // bits per pixel
                writeLE(out, 4, images[i].length); // image size
                writeLE(out, 4, offset); // offset
                offset += images[i].length;
            }
            for (byte[] data : images) {
                out.write(data);
            }
        }
    }

    static void writeLE(OutputStream out, int bytes, int value) throws IOException {
        for (int i = 0; i < bytes; i++) {
            out.write(value & 0xFF);
            value >>= 8;
        }
    }

    static int getPngWidth(byte[] png) {
        return ByteBuffer.wrap(png, 16, 4).getInt();
    }

    static int getPngHeight(byte[] png) {
        return ByteBuffer.wrap(png, 20, 4).getInt();
    }
}

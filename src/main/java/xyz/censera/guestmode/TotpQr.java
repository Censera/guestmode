package xyz.censera.guestmode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

final class TotpQr {
    private TotpQr() {
    }

    static void send(Player player, String uri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 41, 41);
            for (int y = 0; y < matrix.getHeight(); y += 2) {
                StringBuilder line = new StringBuilder(matrix.getWidth());
                for (int x = 0; x < matrix.getWidth(); x++) {
                    boolean top = matrix.get(x, y);
                    boolean bottom = y + 1 < matrix.getHeight() && matrix.get(x, y + 1);
                    line.append(top
                            ? (bottom ? '█' : '▀')
                            : (bottom ? '▄' : ' '));
                }
                player.sendMessage(Component.text(line.toString(), NamedTextColor.WHITE));
            }
        } catch (WriterException e) {
            player.sendMessage(Component.text("Could not generate the 2FA QR code.", NamedTextColor.RED));
        }
    }
}

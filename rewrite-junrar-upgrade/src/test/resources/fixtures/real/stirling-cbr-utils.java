package fixture.stirling;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

class CbrUtils {
    void readImages(File input) throws IOException {
        Archive archive;
        try {
            archive = new Archive(input);
        } catch (CorruptHeaderException e) {
            throw new IOException("corrupt CBR/RAR header", e);
        } catch (RarException e) {
            throw new IOException("invalid CBR/RAR archive", e);
        }

        try {
            for (FileHeader fileHeader : archive) {
                if (!fileHeader.isDirectory()) {
                    try (InputStream stream = archive.getInputStream(fileHeader)) {
                        consume(fileHeader.getFileName(), stream);
                    } catch (Exception ignored) {
                        // The real repository logs and continues with the next image.
                    }
                }
            }
        } finally {
            archive.close();
        }
    }

    private void consume(String name, InputStream stream) {
    }
}

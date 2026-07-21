package com.github.junrar.bugfixes;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
Test cases for https://github.com/junrar/junrar/issues/86
 */
public class GitHub86MissingDataTest {

    /*
    Data taken from UnRAR
     */
    private static final String[] names = {
            "bushido_red/.readme",
            "bushido_red/appearance/footer.php",
            "bushido_red/appearance/footermsword.php",
            "bushido_red/appearance/footerprint.php",
            "bushido_red/appearance/header.php",
            "bushido_red/appearance/headermsword.php",
            "bushido_red/appearance/headerprint.php",
            "bushido_red/appearance",
            "bushido_red/css/.htaccess",
            "bushido_red/css/wakkamsword.css",
            "bushido_red/css/wakkaprint.css",
            "bushido_red/css/wakkastyle.css",
            "bushido_red/css",
            "bushido_red/icons/.htaccess",
            "bushido_red/icons/1del.gif",
            "bushido_red/icons/1print.gif",
            "bushido_red/icons/1unvisibl.gif",
            "bushido_red/icons/file.gif",
            "bushido_red/icons/key.gif",
            "bushido_red/icons/lock.gif",
            "bushido_red/icons/login.gif",
            "bushido_red/icons/login1.gif",
            "bushido_red/icons/mail.gif",
            "bushido_red/icons/referer.gif",
            "bushido_red/icons/rename.gif",
            "bushido_red/icons/toolbar1.gif",
            "bushido_red/icons/toolbar2.gif",
            "bushido_red/icons/visibl.gif",
            "bushido_red/icons/wacko.ico",
            "bushido_red/icons/web.gif",
            "bushido_red/icons/xml.gif",
            "bushido_red/icons",
            "bushido_red/images/banner-red-w-text.jpg",
            "bushido_red/images/banner-red-wo-text.jpg",
            "bushido_red/images/footer-red.jpg",
            "bushido_red/images",
            "bushido_red/lang/wakka.bg.php",
            "bushido_red/lang/wakka.de.php",
            "bushido_red/lang/wakka.en.php",
            "bushido_red/lang/wakka.es.php",
            "bushido_red/lang/wakka.fr.php",
            "bushido_red/lang/wakka.it.php",
            "bushido_red/lang/wakka.nl.php",
            "bushido_red/lang/wakka.ru.php",
            "bushido_red/lang",
            "bushido_red",
    };

    /*
    Data taken from UnRAR
     */
    private static final String[] times = new String[]{
            "2006-03-11T15:50:25.664526400Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T15:36:01.000000000Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T15:36:43.281998400Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T10:50:00.000000000Z",
            "2006-03-11T10:50:00.985487400Z",
            "2006-03-11T15:52:00.000000000Z",
            "2006-03-11T15:52:59.095148800Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:01.377142800Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.262144000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.865646600Z",
            "2006-03-11T09:48:09.419832000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.583270400Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:49:07.980584000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T10:51:02.031449600Z",
            "2006-03-11T10:51:00.000000000Z",
            "2006-03-11T09:48:10.200955200Z",
            "2006-03-11T09:48:10.200955200Z",
    };

    @Test
    public void testCorruptExtendedTimeData() throws Exception {
        File f = new File(getClass().getResource("gh-86-missing-data.rar").toURI());
        try (Archive archive = new Archive(f)) {
            assertThat(archive.isPasswordProtected()).isFalse();
            assertThat(archive.isEncrypted()).isFalse();

            List<FileHeader> fileHeaders = archive.getFileHeaders();

            assertThat(fileHeaders.size()).isEqualTo(names.length);

            for (int i = 0; i < fileHeaders.size(); i++) {
                FileHeader hd = fileHeaders.get(i);
                assertThat(hd.getFileName().replace('\\', '/')).isEqualTo(names[i]);
                assertThat(hd.getHostOS()).isEqualTo(HostSystem.win32);
                assertThat(hd.getLastAccessTime()).isNull();
                assertThat(hd.getCreationTime()).isNull();
                assertThat(hd.getArchivalTime()).isNull();
                assertThat(hd.getLastModifiedTime()).isNotNull();
                Instant time = Instant.parse(times[i]);
                assertThat(hd.getLastModifiedTime().toInstant().getNano()).isEqualTo(time.getNano());
            }
        }
    }
}

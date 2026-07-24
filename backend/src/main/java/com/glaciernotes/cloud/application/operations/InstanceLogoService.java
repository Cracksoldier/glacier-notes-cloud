package com.glaciernotes.cloud.application.operations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class InstanceLogoService {
    private static final int MAXIMUM_BYTES = 2 * 1024 * 1024;
    private final EntityManager entityManager;

    public InstanceLogoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void replace(InputStream input) {
        if (input == null) throw OperationalFailure.invalid("An instance-logo file is required.");
        try {
            byte[] bytes = input.readNBytes(MAXIMUM_BYTES + 1);
            if (bytes.length > MAXIMUM_BYTES) {
                throw new jakarta.ws.rs.ClientErrorException(413);
            }
            String type = contentType(bytes);
            File probe = Files.createTempFile("glacier-logo-probe-", ".img").toFile();
            try {
                Files.write(probe.toPath(), bytes);
                if (ImageIO.read(probe) == null) {
                    throw new jakarta.ws.rs.NotSupportedException("Unsupported instance-logo image.");
                }
            } finally {
                Files.deleteIfExists(probe.toPath());
            }
            entityManager.createNativeQuery("""
                insert into instance_logo(singleton_key,content,content_type,byte_size,checksum,updated_at)
                values (1,:content,:type,:size,:checksum,current_timestamp)
                on conflict(singleton_key) do update set content=excluded.content,
                  content_type=excluded.content_type,byte_size=excluded.byte_size,
                  checksum=excluded.checksum,updated_at=current_timestamp
                """).setParameter("content", bytes).setParameter("type", type)
                .setParameter("size", (long) bytes.length).setParameter("checksum", checksum(bytes))
                .executeUpdate();
        } catch (IOException failure) {
            throw OperationalFailure.invalid("The instance logo could not be read.");
        }
    }

    @Transactional
    public void delete() {
        entityManager.createNativeQuery("delete from instance_logo").executeUpdate();
    }

    @Transactional
    public Download download() {
        var rows = entityManager.createNativeQuery(
            "select content,content_type,checksum from instance_logo where singleton_key=1").getResultList();
        if (rows.isEmpty()) throw OperationalFailure.notFound();
        Object[] row = (Object[]) rows.getFirst();
        try {
            File output = Files.createTempFile("glacier-logo-", ".img").toFile();
            Files.write(output.toPath(), (byte[]) row[0]);
            output.deleteOnExit();
            return new Download(output, row[1].toString(), row[2].toString());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read instance logo", failure);
        }
    }

    private String contentType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
            && bytes[2] == 0x4e && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
            && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (bytes.length >= 12 && new String(bytes, 0, 4).equals("RIFF")
            && new String(bytes, 8, 4).equals("WEBP")) return "image/webp";
        throw new jakarta.ws.rs.NotSupportedException("Unsupported instance-logo image.");
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Download(File file, String contentType, String checksum) {}
}

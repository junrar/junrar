/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.crypt;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Rijndael {
    public static Cipher buildDecoder(final String password, byte[] salt) {
        byte[] AESInit = new byte[16];
        byte[] AESKey = new byte[16];
        int rawLength = 2 * password.length();
        byte[] rawpsw = new byte[rawLength + 8];
        byte[] pwd = password.getBytes();
        for (int i = 0; i < password.length(); i++) {
            rawpsw[i * 2] = pwd[i];
            rawpsw[i * 2 + 1] = 0;
        }
        for (int i = 0; i < salt.length; i++) {
            rawpsw[i + rawLength] = salt[i];
        }

        // SHA-1
        try {
            MessageDigest sha = MessageDigest.getInstance("sha-1");

            final int HashRounds = 0x40000;
            final int xh = HashRounds / 16;

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] digest = null;

            for (int i = 0; i < HashRounds; i++) {
                bout.write(rawpsw);
                bout.write(new byte[] {(byte) i, (byte) (i >> 8), (byte) (i >> 16)});

                if (i % xh == 0) {
                    byte[] input = bout.toByteArray();
                    sha.update(input);
                    digest = sha.digest();
                    AESInit[i / xh] = digest[19];
                }
            }

            sha.update(bout.toByteArray());
            digest = sha.digest();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    AESKey[i * 4 + j] = (byte) (((digest[i * 4] * 0x1000000) & 0xff000000
                            | ((digest[i * 4 + 1] * 0x10000) & 0xff0000)
                            | ((digest[i * 4 + 2] * 0x100) & 0xff00)
                            | digest[i * 4 + 3] & 0xff) >> (j * 8));
                }
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(AESKey, "AES"),
                    new IvParameterSpec(AESInit));
            return cipher;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.amazonaws.cloudhsm.examples;

import com.cavium.cfm2.CFM2Exception;
import com.cavium.cfm2.Util;
import com.cavium.key.*;
import com.cavium.key.parameter.CaviumAESKeyGenParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.util.Base64;

/**
 * This sample demonstrates how to use AES to wrap and unwrap a key into and out of the HSM.
 */
public class AESWrappingRunner {
    public static void main(String[] args)
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        try {
            Security.addProvider(new com.cavium.provider.CaviumProvider());
        } catch (IOException ex) {
            System.out.println(ex);
            return;
        }

        // Wrapping keys must be persistent.
        CaviumKey wrappingKey = generateExtractableKey(256, "Wrapping Key", true);

        // Extractable keys must be marked extractable.
        CaviumKey extractableKey = generateExtractableKey(256, "Test Extractable Key", false);

        try {
            // Using the wrapping key, wrap and unwrap the extractable key.
            wrap(wrappingKey, extractableKey);

            // Clean up the keys.
            Util.deleteKey(wrappingKey);
            Util.deleteKey(extractableKey);
        } catch (CFM2Exception ex) {
            ex.printStackTrace();
            System.out.printf("Failed to delete key handles: %d\n", wrappingKey.getHandle());
        }
    }

    /**
     * Using the wrapping key, wrap and unwrap the extractable key.
     * @param wrappingKey
     * @param extractableKey
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws CFM2Exception
     */
    private static void wrap(CaviumKey wrappingKey, CaviumKey extractableKey)
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, CFM2Exception {

        CaviumAESKeyGenParameterSpec spec = new CaviumAESKeyGenParameterSpec();

        // Wrap the extractable key using the wrappingKey.
        byte[] wrappedBytes = Util.wrapKey(wrappingKey, extractableKey);

        // Unwrap the wrapped key using the wrapping key.
        Key unwrappedExtractableKey = Util.unwrapKey(wrappingKey, wrappedBytes, "AES", Cipher.SECRET_KEY, spec);

        // Compare the two keys.
        // Notice that extractable keys can be exported from the HSM using the .getEncoded() method.
        assert(extractableKey.getEncoded().equals(unwrappedExtractableKey.getEncoded()));
        System.out.printf("Verified unwrapped data: %s\n", Base64.getEncoder().encodeToString(unwrappedExtractableKey.getEncoded()));
    }

    /**
     * Generate an extractable key that can be toggled persistent.
     * AES wrapping keys are required to be persistent. The keys being wrapped can be persistent or session keys.
     * @param keySizeInBits
     * @param keyLabel
     * @return CaviumKey that is extractable and persistent.
     */
    private static CaviumKey generateExtractableKey(int keySizeInBits, String keyLabel, boolean isPersistent) {
        boolean isExtractable = true;

        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES", "Cavium");

            CaviumAESKeyGenParameterSpec aesSpec = new CaviumAESKeyGenParameterSpec(keySizeInBits, keyLabel, isExtractable, isPersistent);
            keyGen.init(aesSpec);
            SecretKey aesKey = keyGen.generateKey();

            return (CaviumKey) aesKey;

        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
        } catch (Exception e) {
            if (CFM2Exception.isAuthenticationFailure(e)) {
                System.out.println("Detected invalid credentials");
            } else if (CFM2Exception.isClientDisconnectError(e)) {
                System.out.println("Detected daemon network failure");
            }

            e.printStackTrace();
        }

        return null;
    }

}

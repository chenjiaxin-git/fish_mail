package com.fishmall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SdkWeChatPayClientTest {
    @TempDir Path folder;
    KeyPair merchant, platform;
    PayProperties properties;
    SdkWeChatPayClient client;
    final ObjectMapper json = new ObjectMapper();
    static final String KEY = "01234567890123456789012345678901";

    @BeforeEach void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        merchant = generator.generateKeyPair(); platform = generator.generateKeyPair();
        Path privateKey = folder.resolve("merchant.pem"), publicKey = folder.resolve("wechat.pem");
        Files.writeString(privateKey, pem("PRIVATE KEY", merchant.getPrivate().getEncoded()));
        Files.writeString(publicKey, pem("PUBLIC KEY", platform.getPublic().getEncoded()));
        properties = new PayProperties(true, "wx-test", "mch-test", "merchant-serial", privateKey.toString(),
                "PUB_KEY_ID_TEST", publicKey.toString(), KEY, "https://example.test/api/v1/payments/wechat/notify", 1000);
        client = new SdkWeChatPayClient(properties);
    }
    @Test void realSignedEncryptedNotificationIsVerifiedAndDecrypted() throws Exception {
        String body = body("TRANSACTION.SUCCESS", false);
        var result = client.verify(body, headers(body, Instant.now().getEpochSecond()));
        assertThat(result.eventId()).isEqualTo("event-1");
        assertThat(result.trade().outTradeNo()).isEqualTo("FM1");
        assertThat(result.trade().total()).isEqualTo(3600);
        assertThat(result.trade().openid()).isEqualTo("openid-test");
    }
    @Test void bodySignatureSerialAndTimestampTamperingAreRejected() throws Exception {
        String body = body("TRANSACTION.SUCCESS", false); var headers = headers(body, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> client.verify(body + " ", headers)).isInstanceOf(BusinessException.class);
        var wrongSerial = new HashMap<>(headers); wrongSerial.put("Wechatpay-Serial", "PUB_KEY_ID_OTHER");
        assertThatThrownBy(() -> client.verify(body, wrongSerial)).isInstanceOf(BusinessException.class);
        var old = headers(body, Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> client.verify(body, old)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.verify(body, Map.of())).isInstanceOf(BusinessException.class);
    }
    @Test void validSignatureDoesNotBypassDecryptionOrEventChecks() throws Exception {
        String badCipher = body("TRANSACTION.SUCCESS", true);
        assertThatThrownBy(() -> client.verify(badCipher, headers(badCipher, Instant.now().getEpochSecond())))
                .isInstanceOf(BusinessException.class);
        String wrongEvent = body("TRANSACTION.CLOSED", false);
        assertThatThrownBy(() -> client.verify(wrongEvent, headers(wrongEvent, Instant.now().getEpochSecond())))
                .isInstanceOf(BusinessException.class);
    }
    @Test void miniProgramSignatureUsesMerchantPrivateKeyAndExactMessage() throws Exception {
        var params = client.sign("test-prepay");
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(merchant.getPublic());
        verifier.update(("wx-test\n" + params.get("timeStamp") + "\n" + params.get("nonceStr") + "\nprepay_id=test-prepay\n").getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(params.get("paySign")))).isTrue();
        assertThat(params.get("signType")).isEqualTo("RSA");
    }
    @Test void sdkResponseValidatorRejectsUnsignedOrModifiedResponse() throws Exception {
        var config = new RSAPublicKeyConfig.Builder().merchantId(properties.mchId()).merchantSerialNumber(properties.merchantSerialNo())
                .privateKeyFromPath(properties.privateKeyPath()).publicKeyFromPath(properties.publicKeyPath())
                .publicKeyId(properties.publicKeyId()).apiV3Key(KEY).build();
        String body = "{\"prepay_id\":\"test\"}";
        HttpHeaders headers = new HttpHeaders(headers(body, Instant.now().getEpochSecond()));
        assertThat(config.createValidator().validate(headers, body)).isTrue();
        assertThat(config.createValidator().validate(headers, body + " ")).isFalse();
        assertThatThrownBy(() -> config.createValidator().validate(new HttpHeaders(), body)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompleteOrInsecureConfigurationFailsAndSecretsAreRedacted() {
        var bad = new PayProperties(true, "wx", "mch", "serial", properties.privateKeyPath(), "PUB_KEY_ID_TEST",
                properties.publicKeyPath(), KEY, "http://example.test/notify", 1000);
        assertThatThrownBy(() -> new SdkWeChatPayClient(bad)).isInstanceOf(IllegalStateException.class);
        assertThat(properties.toString()).doesNotContain(KEY, properties.privateKeyPath());
    }
    private String body(String eventType, boolean tamper) throws Exception {
        String plain = json.writeValueAsString(Map.of("appid", "wx-test", "mchid", "mch-test", "out_trade_no", "FM1",
                "transaction_id", "tx-1", "trade_state", "SUCCESS", "trade_type", "JSAPI", "success_time", "2026-10-29T12:00:00+08:00",
                "payer", Map.of("openid", "openid-test"), "amount", Map.of("total", 3600, "payer_total", 3500, "currency", "CNY")));
        String nonce = "123456789012", associated = "transaction";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        if (tamper) encrypted[0] ^= 1;
        return json.writeValueAsString(Map.of("id", "event-1", "event_type", eventType, "resource", Map.of("algorithm", "AEAD_AES_256_GCM",
                "nonce", nonce, "associated_data", associated, "ciphertext", Base64.getEncoder().encodeToString(encrypted))));
    }
    private Map<String,String> headers(String body, long timestamp) throws Exception {
        String nonce = "notification-nonce";
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(platform.getPrivate());
        signer.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        return Map.of("Wechatpay-Serial", "PUB_KEY_ID_TEST", "Wechatpay-Timestamp", Long.toString(timestamp),
                "Wechatpay-Nonce", nonce, "Wechatpay-Signature", Base64.getEncoder().encodeToString(signer.sign()));
    }
    private String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes) + "\n-----END " + type + "-----\n";
    }
}

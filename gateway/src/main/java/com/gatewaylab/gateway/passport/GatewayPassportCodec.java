package com.gatewaylab.gateway.passport;

import com.gatewaylab.gateway.json.JsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Passport를 HTTP 헤더에 안전하게 전파하기 위한 직렬화 책임을 가진다.
 * Base64는 인코딩일 뿐 보안 기능이 아니므로, 신뢰 경계가 넓어지면 서명 또는 암호화를 추가해야 한다.
 */
@Component
@RequiredArgsConstructor
public class GatewayPassportCodec {

    private final JsonCodec jsonCodec;

    public String encode(GatewayPassport passport) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonCodec.writeBytes(passport));
    }

    public GatewayPassport decode(String encodedPassport) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedPassport);
            return jsonCodec.read(json, GatewayPassport.class);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid gateway passport", exception);
        }
    }

}

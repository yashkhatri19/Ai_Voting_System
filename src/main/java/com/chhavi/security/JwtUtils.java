package com.chhavi.security;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

@Component
public class JwtUtils {

    private static final String SECRET = "VoteIndiaSecureVotingPortalJwtSecretKey2026!!!";
    private static final String ISSUER = "VOTE-INDIA";
    
    // Access token valid for 1 hour
    private static final long ACCESS_TOKEN_EXPIRY = 60 * 60 * 1000L; 
    
    // Refresh token valid for 7 days
    private static final long REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000L;

    public String generateAccessToken(String email, String role) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim("role", role)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                .sign(Algorithm.HMAC256(SECRET));
    }

    public String generateRefreshToken(String email) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
                .sign(Algorithm.HMAC256(SECRET));
    }

    public boolean validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        return decodedJWT.getSubject();
    }

    public String getRoleFromToken(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        return decodedJWT.getClaim("role").asString();
    }
}

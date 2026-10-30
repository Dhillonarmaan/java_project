package com.keybridge.service;

import com.keybridge.dto.token.CreateTokenRequest;
import com.keybridge.dto.token.TokenResponse;
import com.keybridge.entity.Token;
import com.keybridge.entity.User;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.enums.Role;
import com.keybridge.enums.TokenStatus;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.NotFoundException;
import com.keybridge.repository.TokenRepository;
import com.keybridge.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TokenService {

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

    public TokenService(TokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TokenResponse> listTokens(User currentUser) {
        List<Token> tokens = (currentUser.getRole() == Role.USER)
                ? tokenRepository.findByOwnerId(currentUser.getId())
                : tokenRepository.findAll();

        return tokens.stream()
                .map(TokenResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TokenResponse getToken(Long id) {
        Token token = tokenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found with ID: " + id));
        return TokenResponse.fromEntity(token);
    }

    @Transactional
    public TokenResponse createToken(CreateTokenRequest req) {
        String serial = req.serialNo().trim();
        if (tokenRepository.findBySerialNo(serial).isPresent()) {
            throw new BadRequestException("Token with serial number " + serial + " already exists");
        }

        KeyStorageType storage = req.keyStorage() != null ? req.keyStorage() : KeyStorageType.SOFT_P12;
        if (storage == KeyStorageType.PKCS11 && req.pkcs11Slot() == null) {
            throw new BadRequestException("pkcs11Slot is required for a PKCS11 token");
        }
        if (storage != KeyStorageType.PKCS11 && req.pkcs11Slot() != null) {
            throw new BadRequestException("pkcs11Slot is only allowed for a PKCS11 token");
        }

        Token token = new Token(serial, req.label() != null ? req.label().trim() : null);
        token.setKeyStorage(storage);
        token.setPkcs11Slot(req.pkcs11Slot());
        tokenRepository.save(token);
        return TokenResponse.fromEntity(token);
    }

    @Transactional
    public TokenResponse assignToken(Long id, Long userId) {
        Token token = tokenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found with ID: " + id));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        token.setOwner(user);
        token.setStatus(TokenStatus.ASSIGNED);
        token.setAssignedDate(LocalDate.now());
        tokenRepository.save(token);
        return TokenResponse.fromEntity(token);
    }

    @Transactional
    public TokenResponse updateStatus(Long id, TokenStatus status) {
        Token token = tokenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found with ID: " + id));

        token.setStatus(status);
        if (status == TokenStatus.AVAILABLE) {
            token.setOwner(null);
            token.setAssignedDate(null);
        }
        tokenRepository.save(token);
        return TokenResponse.fromEntity(token);
    }
}
